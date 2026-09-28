#!/usr/bin/env bash
# Seed a photo-printing catalogue into a running ShopFlow stack (via the gateway).
# Needs: curl.  Usage:  bash scripts/seed-catalog.sh  [GATEWAY_URL]
set -euo pipefail

GW="${1:-http://localhost:8080}"
ADMIN_EMAIL="admin@shopflow.local"       # must be in bootstrap-admin-emails
PW="password1"

jget() { # extract a top-level JSON string value: jget <key> <<< "$json"
  sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"
}

echo "register admin (ok if already exists)"
curl -fsS -X POST "$GW/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PW\",\"displayName\":\"Admin\"}" >/dev/null 2>&1 || true

TOKEN=$(curl -fsS -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PW\"}" | jget accessToken)
[ -n "$TOKEN" ] || { echo "login failed"; exit 1; }
echo "got admin token"

mkcat() { # mkcat "Name"  -> echoes category id
  curl -fsS -X POST "$GW/api/categories" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d "{\"name\":\"$1\"}" | jget id
}

mkprod() { # mkprod <catId> <sku> <name> <price> <qty> <imgSeed> <description>
  local cid="$1" sku="$2" name="$3" price="$4" qty="$5" seed="$6" desc="$7"
  local pid
  pid=$(curl -fsS -X POST "$GW/api/products" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d "{
      \"sku\":\"$sku\",\"name\":\"$name\",\"description\":\"$desc\",
      \"price\":$price,\"categoryId\":\"$cid\",
      \"imageUrl\":\"https://picsum.photos/seed/$seed/800/800\"
    }" | jget id)
  curl -fsS -X POST "$GW/api/inventory/stock" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d "{\"productId\":\"$pid\",\"quantity\":$qty}" >/dev/null
  echo "  + $name  (\$$price, stock $qty)"
}

echo "categories"
PRINTS=$(mkcat "Photo Prints")
WALL=$(mkcat "Wall Art")
BOOKS=$(mkcat "Photo Books")
GIFTS=$(mkcat "Cards & Gifts")

echo "products"
mkprod "$PRINTS" "PRT-CLASSIC-4X6"  "Classic Photo Print 4x6"     0.19  5000 print-classic  "Glossy or matte, printed on genuine photo paper. Rich colour, true blacks."
mkprod "$PRINTS" "PRT-SQUARE-5X5"   "Square Print 5x5"            0.39  4000 print-square   "The Instagram-perfect square, with a subtle white border."
mkprod "$PRINTS" "PRT-MATTE-8X10"   "Matte Print 8x10"           2.99  1500 print-matte    "Fingerprint-resistant matte finish for framing and gifting."
mkprod "$PRINTS" "PRT-LARGE-16X20"  "Large Format Print 16x20"  14.00   400 print-large    "Poster-scale prints with gallery-grade ink density."

mkprod "$WALL"   "WAL-CANVAS-16X16" "Framed Canvas 16x16"       59.00   250 wall-canvas    "Hand-stretched cotton canvas on a solid wood frame. Ready to hang."
mkprod "$WALL"   "WAL-METAL-12X18"  "Metal Print 12x18"         49.00   200 wall-metal     "Dye infused into aluminium - luminous, waterproof, fade-resistant."
mkprod "$WALL"   "WAL-ACRYLIC-20X30" "Acrylic Wall Panel 20x30" 129.00   90 wall-acrylic   "Face-mounted to 5mm acrylic for astonishing depth and gloss."
mkprod "$WALL"   "WAL-WOOD-11X14"   "Wood Print 11x14"          34.00   300 wall-wood      "Your photo printed straight onto natural birch - the grain shows through."

mkprod "$BOOKS"  "BOK-SOFT-8X8"     "Softcover Photo Book 8x8"   19.00   600 book-soft      "20 pages, lay-flat-friendly binding, matte laminate cover."
mkprod "$BOOKS"  "BOK-LAYFLAT-12X12" "Layflat Album 12x12"      89.00   150 book-layflat   "Thick rigid pages open to a seamless 24-inch panorama."
mkprod "$BOOKS"  "BOK-MINI-6X6"     "Mini Brag Book 6x6"         9.99   900 book-mini      "Pocket-size, three-pack. Perfect for grandparents."

mkprod "$GIFTS"  "GFT-MUG-11OZ"     "Photo Mug 11oz"            12.99   800 gift-mug       "Dishwasher-safe wrap print. Colour stays put wash after wash."
mkprod "$GIFTS"  "GFT-CAL-A4"       "Custom Wall Calendar"       24.99   500 gift-calendar  "13 spreads, starts on any month, holidays for your region."
mkprod "$GIFTS"  "GFT-CARDS-10PK"   "Greeting Cards (10-pack)"    9.00  1200 gift-cards     "5x7 folded cards with envelopes. Your photo, your message inside."
mkprod "$GIFTS"  "GFT-MAGNET-6PK"   "Photo Magnet Set (6)"        7.50  1000 gift-magnet    "Flexible fridge magnets with a soft-touch matte face."

echo
echo "done. Browse: $GW/api/products?size=50"
