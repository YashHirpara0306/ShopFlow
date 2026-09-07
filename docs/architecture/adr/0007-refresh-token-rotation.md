# ADR-0007: Refresh tokens - opaque, hashed, rotating, with reuse detection

**Status:** Accepted (Phase 4)

## Context

Access tokens are short-lived JWTs (15 min). Clients need a way to stay signed in without
re-entering a password, and the system needs a way to *revoke* a session (logout, password change,
suspected theft) - which a stateless JWT alone cannot provide.

## Decision

Refresh tokens are:

- **Opaque**, not JWTs - 256 bits of `SecureRandom`, Base64URL-encoded. They carry no claims; they
  are looked up server-side.
- **Stored hashed** - only the SHA-256 hex of the token is persisted (`refresh_token.token_hash`,
  UNIQUE). A database leak yields no usable tokens. (Plain SHA-256, not BCrypt: the input is
  high-entropy random, so slow hashing buys nothing.)
- **Rotated on every use** - `/api/auth/refresh` issues a new access token *and* a new refresh
  token, marks the presented one `revoked`, and links `replaced_by_id`.
- **Grouped into a family** - `family_id` ties a login's whole rotation chain together.
- **Reuse-detecting** - presenting an already-revoked token (a rotated-away or logged-out token)
  is treated as theft: the entire family is revoked, forcing a fresh login. This revocation is
  committed even though the request then fails (`@Transactional(noRollbackFor = ...)`).
- **Time-bounded** - 7-day expiry (`shopflow.jwt.refresh-token-ttl`).

Logout revokes the presented refresh token (idempotent: unknown token -> still `204`).

## Consequences

**Positive**
- Real revocation (logout, theft response) despite stateless access tokens.
- A stolen refresh token is single-use: the first party to use it after the thief triggers family
  revocation, so at most one extra access token is minted before the session dies.
- DB compromise does not expose usable tokens.

**Negative / accepted trade-offs**
- A stateful table and a DB round-trip on every refresh. Acceptable: refreshes are infrequent
  (every ~15 min per active client) and the table is tiny and index-covered.
- A legitimate client that races two refreshes with the same token will trip reuse detection and be
  logged out. Rare; the fix (client mutex around refresh) is standard.
- Access tokens still cannot be revoked before their 15-min expiry. Accepted; a `jti` denylist in
  Redis is the documented upgrade path.
