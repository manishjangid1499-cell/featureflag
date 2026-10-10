import { useCallback, useEffect, useRef, useState, type SetStateAction } from "react";
import { getApiErrorMessage } from "../api/errors";
import { emptyPage, type PageResponse } from "../types/page";

export function usePagedResource<T>(
  fetchPage: (page: number) => Promise<PageResponse<T>>,
  errorMessage: string,
) {
  const [requestState, setRequestState] = useState(() => ({
    page: 0,
    requestId: 0,
  }));
  const [data, setData] = useState(emptyPage<T>());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [prevRequestId, setPrevRequestId] = useState(requestState.requestId);
  const [prevFetchPage, setPrevFetchPage] = useState(() => fetchPage);

  if (requestState.requestId !== prevRequestId || fetchPage !== prevFetchPage) {
    setPrevRequestId(requestState.requestId);
    setPrevFetchPage(() => fetchPage);
    setLoading(true);
    setError("");
  }

  const requestVersion = useRef(0);
  const reloadWaitersRef = useRef<Array<() => void>>([]);

  const handleSetPage = useCallback((newPageOrUpdater: SetStateAction<number>) => {
    setRequestState((prev) => {
      const next = typeof newPageOrUpdater === "function" ? newPageOrUpdater(prev.page) : newPageOrUpdater;
      if (next === prev.page) return prev;
      return {
        page: next,
        requestId: prev.requestId + 1,
      };
    });
  }, []);

  useEffect(() => {
    let ignore = false;
    const version = ++requestVersion.current;
    let curTarget = requestState.page;
    let isCorrecting = false;
    const currentWaiters = reloadWaitersRef.current;
    reloadWaitersRef.current = [];

    let hasSettledWaiters = false;
    const settleWaiters = () => {
      if (hasSettledWaiters) return;
      hasSettledWaiters = true;
      for (const notify of currentWaiters) notify();
    };

    const executePipeline = async () => {
      try {
        const response = await fetchPage(curTarget);
        if (ignore || version !== requestVersion.current) return;

        const lastPage = Math.max(0, response.totalPages - 1);
        if (curTarget > lastPage) {
          isCorrecting = true;
          reloadWaitersRef.current.push(...currentWaiters);
          setRequestState((prev) => (prev.page === lastPage ? prev : { ...prev, page: lastPage }));
          return;
        }

        setData(response);
        setError("");
      } catch (failure: unknown) {
        if (!ignore && version === requestVersion.current) {
          setError(getApiErrorMessage(failure, errorMessage));
        }
      } finally {
        if (!isCorrecting) {
          if (!ignore && version === requestVersion.current) {
            setLoading(false);
          }
          settleWaiters();
        }
      }
    };

    void executePipeline();

    return () => {
      ignore = true;
      requestVersion.current += 1;
      if (!isCorrecting) {
        settleWaiters();
      }
    };
  }, [requestState.page, requestState.requestId, fetchPage, errorMessage]);

  useEffect(() => {
    return () => {
      const pending = reloadWaitersRef.current;
      reloadWaitersRef.current = [];
      for (const notify of pending) notify();
    };
  }, []);

  const reload = useCallback(
    (requestedPage?: number): Promise<void> => {
      return new Promise<void>((resolve) => {
        let settled = false;
        const safeResolve = () => {
          if (settled) return;
          settled = true;
          resolve();
        };
        reloadWaitersRef.current.push(safeResolve);
        setRequestState((prev) => {
          const next = requestedPage !== undefined ? requestedPage : prev.page;
          return {
            page: next,
            requestId: prev.requestId + 1,
          };
        });
      });
    },
    []
  );

  const setItems = useCallback((update: SetStateAction<T[]>) => {
    setData((previous) => ({
      ...previous,
      content: typeof update === "function" ? update(previous.content) : update,
    }));
  }, []);

  return {
    items: data.content,
    setItems,
    data,
    page: requestState.page,
    setPage: handleSetPage,
    loading,
    error,
    reload,
  };
}
