import { useCallback, useEffect, useRef, useState, type SetStateAction } from "react";
import { getApiErrorMessage } from "../api/errors";
import { emptyPage, type PageResponse } from "../types/page";

export function usePagedResource<T>(
  fetchPage: (page: number) => Promise<PageResponse<T>>,
  errorMessage: string,
) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(emptyPage<T>());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const requestVersion = useRef(0);

  const load = useCallback(async (requestedPage: number) => {
    const version = ++requestVersion.current;
    setLoading(true);
    setError("");
    try {
      const response = await fetchPage(requestedPage);
      if (version !== requestVersion.current) return;
      const lastPage = Math.max(0, response.totalPages - 1);
      if (requestedPage > lastPage) {
        setPage(lastPage);
        return;
      }
      setData(response);
    } catch (failure: unknown) {
      if (version === requestVersion.current) {
        setError(getApiErrorMessage(failure, errorMessage));
      }
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }, [fetchPage, errorMessage]);

  useEffect(() => {
    void load(page);
    return () => { requestVersion.current += 1; };
  }, [page, load]);

  const reload = useCallback(async (requestedPage = page) => {
    if (requestedPage !== page) setPage(requestedPage);
    else await load(page);
  }, [page, load]);

  const setItems = useCallback((update: SetStateAction<T[]>) => {
    setData(previous => ({
      ...previous,
      content: typeof update === "function" ? update(previous.content) : update,
    }));
  }, []);

  return { items: data.content, setItems, data, page, setPage, loading, error, reload };
}
