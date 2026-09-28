#!/usr/bin/env bash
# Add a broad general-merchandise catalogue (Electronics, Clothing, Food, ...) to a
# running ShopFlow stack, via the gateway.  Needs: curl.
#   bash scripts/seed-more.sh  [GATEWAY_URL]
set -uo pipefail   # not -e: a single bad product should not abort the whole seed

GW="${1:-http://localhost:8080}"
ADMIN_EMAIL="admin@shopflow.local"
PW="password1"

jget() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }

curl -fsS -X POST "$GW/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PW\",\"displayName\":\"Admin\"}" >/dev/null 2>&1 || true
TOKEN=$(curl -fsS -X POST "$GW/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PW\"}" | jget accessToken)
[ -n "$TOKEN" ] || { echo "login failed"; exit 1; }
echo "admin ok"

# create category, or reuse the existing one with the same name
mkcat() {
  local name="$1" id
  id=$(curl -fsS -X POST "$GW/api/categories" -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' -d "{\"name\":\"$name\"}" 2>/dev/null | jget id || true)
  if [ -z "$id" ]; then
    id=$(curl -fsS "$GW/api/categories" | tr '}' '\n' \
          | sed -n "s/.*\"id\":\"\([0-9a-f-]*\)\"[^}]*\"name\":\"$name\".*/\1/p" | head -1)
  fi
  echo "$id"
}

mkprod() { # <catId> <sku> <name> <price> <qty> <imgSeed> <description>
  local pid
  pid=$(curl -fsS -X POST "$GW/api/products" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d "{
      \"sku\":\"$2\",\"name\":\"$3\",\"description\":\"$7\",
      \"price\":$4,\"categoryId\":\"$1\",
      \"imageUrl\":\"https://picsum.photos/seed/$6/800/800\"
    }" | jget id)
  [ -n "$pid" ] || { echo "  ! failed: $3"; return; }
  curl -fsS -X POST "$GW/api/inventory/stock" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' -d "{\"productId\":\"$pid\",\"quantity\":$5}" >/dev/null
  echo "  + $3  (\$$4, stock $5)"
}

echo "== Electronics =="
ELEC=$(mkcat "Electronics")
mkprod "$ELEC" "EL-EARBUDS-PRO"  "Wireless Earbuds Pro"        79.00  600 el-earbuds  "Active noise cancelling, 30h with the case, USB-C fast charge."
mkprod "$ELEC" "EL-KB-MECH-75"   "Mechanical Keyboard 75%"     119.00 300 el-keyboard "Hot-swap switches, gasket mount, PBT keycaps, wired + 2.4GHz."
mkprod "$ELEC" "EL-MOUSE-ERGO"   "Ergonomic Wireless Mouse"    44.99  800 el-mouse    "Vertical grip, 4000 DPI, silent clicks, 3-device switching."
mkprod "$ELEC" "EL-PWRBANK-20K"  "Power Bank 20000mAh"         39.99 1000 el-powerbank "65W USB-C PD, charges a laptop. Airline-safe capacity."
mkprod "$ELEC" "EL-MONITOR-27"   "27-inch 144Hz IPS Monitor"      229.00 150 el-monitor  "2560x1440, 1ms, HDR400, height-adjust stand, USB hub."
mkprod "$ELEC" "EL-WEBCAM-1080"  "1080p Streaming Webcam"      54.00  500 el-webcam   "Dual mics, autofocus, privacy shutter, plug-and-play."

echo "== Clothing =="
CLO=$(mkcat "Clothing")
mkprod "$CLO" "CL-TEE-PIMA"      "Pima Cotton T-Shirt"          28.00 1200 cl-tee     "Buttery-soft long-staple cotton. Holds shape wash after wash."
mkprod "$CLO" "CL-HOODIE-FLEECE" "Heavyweight Fleece Hoodie"    62.00  700 cl-hoodie  "480gsm brushed-back fleece, double-lined hood, ribbed cuffs."
mkprod "$CLO" "CL-JEANS-SLIM"    "Slim Stretch Jeans"           74.00  500 cl-jeans   "Comfort stretch denim with a clean taper. Fades honestly."
mkprod "$CLO" "CL-SOCKS-MERINO"  "Merino Crew Socks (3-pack)"   32.00  900 cl-socks   "Temperature-regulating merino blend, cushioned sole, seamless toe."
mkprod "$CLO" "CL-JACKET-RAIN"   "Packable Rain Jacket"         98.00  350 cl-jacket  "10k/10k waterproof-breathable, taped seams, stuffs into its pocket."
mkprod "$CLO" "CL-CAP-6PANEL"    "6-Panel Cotton Cap"           24.00  800 cl-cap     "Unstructured crown, adjustable strap, pre-curved brim."

echo "== Food & Drink =="
FOOD=$(mkcat "Food & Drink")
mkprod "$FOOD" "FD-COFFEE-1KG"   "Single-Origin Coffee 1kg"     34.00  600 fd-coffee  "Washed Ethiopian, roasted for filter. Jasmine, bergamot, stone fruit."
mkprod "$FOOD" "FD-CHOC-DARK"    "70% Dark Chocolate (6 bars)"  27.00  900 fd-choc    "Small-batch, bean-to-bar. Nothing but cocoa, sugar, cocoa butter."
mkprod "$FOOD" "FD-OLIVEOIL-500" "Extra Virgin Olive Oil 500ml" 22.00  700 fd-oil     "Cold-pressed, early-harvest, single estate. Peppery finish."
mkprod "$FOOD" "FD-TEA-SAMPLER"  "Loose-Leaf Tea Sampler"       19.00  800 fd-tea     "Eight tins: black, green, oolong, herbal. Roughly 40 cups."
mkprod "$FOOD" "FD-HONEY-RAW"    "Raw Wildflower Honey 500g"    16.00 1000 fd-honey   "Unfiltered, unpasteurised, from a single apiary. Crystallises - that's good."
mkprod "$FOOD" "FD-HOTSAUCE-3"   "Hot Sauce Trio"               21.00  650 fd-hotsauce "Verde, smoky chipotle, and a scotch-bonnet number. Vinegar-forward."

echo "== Home & Kitchen =="
HOME=$(mkcat "Home & Kitchen")
mkprod "$HOME" "HK-KNIFE-CHEF8"  "8-inch Chef Knife"             89.00  400 hk-knife    "High-carbon stainless, full tang, hand-finished edge at 15 degrees."
mkprod "$HOME" "HK-BOARD-WALNUT" "Walnut Cutting Board"         64.00  350 hk-board    "End-grain, self-healing surface, juice groove, rubber feet."
mkprod "$HOME" "HK-KETTLE-GOOSE" "Gooseneck Pour-Over Kettle"   72.00  300 hk-kettle   "1L, variable temperature to 1 degree, hold mode, counterweighted spout."
mkprod "$HOME" "HK-TOWELS-6"     "Turkish Cotton Towels (6)"    58.00  500 hk-towels   "Long-loop Aegean cotton, gets more absorbent with every wash."
mkprod "$HOME" "HK-CANDLE-SOY"   "Soy Candle 60h"               26.00  900 hk-candle   "Cedar, vetiver, a little smoke. Cotton wick, 60-hour burn."

echo "== Books =="
BKS=$(mkcat "Books")
mkprod "$BKS" "BK-NOVEL-LITFIC"  "The Long Field (Novel)"       18.00  700 bk-novel    "A quiet, sprawling family story about land, memory, and leaving."
mkprod "$BKS" "BK-COOK-WEEKNIGHT" "Weeknight Kitchen (Cookbook)" 32.00 500 bk-cook     "120 recipes under 40 minutes, pantry-first, photographed for real."
mkprod "$BKS" "BK-SCI-COSMOS"    "A Short History of Almost Everything" 22.00 600 bk-sci "Big ideas in physics, chemistry and biology, told without the maths."
mkprod "$BKS" "BK-KIDS-PICTURE"  "Where the Moon Goes (Kids)"   15.00  800 bk-kids     "A bedtime picture book with fold-out night skies."

echo "== Sports & Outdoors =="
SPO=$(mkcat "Sports & Outdoors")
mkprod "$SPO" "SP-BOTTLE-1L"     "Insulated Bottle 1L"          33.00  900 sp-bottle   "Keeps cold 24h / hot 12h. Wide mouth, cap doubles as a cup."
mkprod "$SPO" "SP-MAT-YOGA"      "Cork Yoga Mat"                68.00  400 sp-mat      "Natural cork over recycled rubber, grippier when you sweat, 4mm."
mkprod "$SPO" "SP-BANDS-SET"     "Resistance Bands Set (5)"     26.00 1000 sp-bands    "5 to 50kg, fabric bands, door anchor and handles included."
mkprod "$SPO" "SP-DAYPACK-22"    "22L Hiking Daypack"           84.00  350 sp-daypack  "Ventilated back panel, rain cover, trekking-pole loops, 700g."

echo
echo "done. Browse: $GW/api/products?size=100"
