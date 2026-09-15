interface PaginationControlsProps {
  page: number;
  totalPages: number;
  totalElements: number;
  disabled?: boolean;
  onPageChange: (page: number) => void;
}

export function PaginationControls({
  page,
  totalPages,
  totalElements,
  disabled = false,
  onPageChange,
}: PaginationControlsProps) {
  if (totalPages <= 1) return null;

  return (
    <nav
      aria-label="Pagination"
      className="responsive-row"
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "12px",
        padding: "14px 4px",
        color: "#4b5563",
        fontSize: "12px",
      }}
    >
      <span>{totalElements} records</span>
      <div className="responsive-actions" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
        <button
          type="button"
          disabled={disabled || page === 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </button>
        <span>
          Page {page + 1} of {totalPages}
        </span>
        <button
          type="button"
          disabled={disabled || page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </button>
      </div>
    </nav>
  );
}
