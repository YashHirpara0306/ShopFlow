import { Link } from "react-router-dom";

export function NotFound() {
  return (
    <div className="container page">
      <div className="empty">
        <h3>Page not found</h3>
        <p className="muted" style={{ marginBottom: 18 }}>
          That link doesn’t lead anywhere.
        </p>
        <Link to="/" className="btn">
          Back home
        </Link>
      </div>
    </div>
  );
}
