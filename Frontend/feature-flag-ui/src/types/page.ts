export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const DEFAULT_PAGE_SIZE = 20;

export const emptyPage = <T>(page = 0, size = DEFAULT_PAGE_SIZE): PageResponse<T> => ({
  content: [],
  page,
  size,
  totalElements: 0,
  totalPages: 0,
});

export const collectAllPages = async <T>(
  fetchPage: (page: number, size: number) => Promise<PageResponse<T>>,
): Promise<T[]> => {
  const size = 100;
  const first = await fetchPage(0, size);
  const content = [...first.content];
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await fetchPage(page, size);
    content.push(...next.content);
  }
  return content;
};
