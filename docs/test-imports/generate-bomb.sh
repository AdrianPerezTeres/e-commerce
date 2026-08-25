#!/usr/bin/env bash
#
# Generates large CSV files for stress-testing import defenses:
#   [6] File size limit (20MB)
#   [7] Row limit (100,000 rows)
#
# Usage:
#   ./generate-bomb.sh            # generates both files
#   ./generate-bomb.sh rows       # 1M rows only (~55MB)
#   ./generate-bomb.sh size       # 25MB file only
#
# These files are NOT committed to git (too large). Generate locally to test.

set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"

generate_row_bomb() {
  local out="$DIR/bomb-1m-rows.csv"
  echo "Generating 1,000,000 row CSV → $out"
  echo "name,sku,description,category,price,stock,weight_kg" > "$out"

  local categories=("Electronics" "Clothing" "Books" "Food & Beverage" "Sports"
                     "Accessories" "Home & Office" "Tools" "Kitchen" "Health")

  for i in $(seq 1 1000000); do
    cat="${categories[$((i % 10))]}"
    price=$(echo "scale=2; ($i % 500) + 0.99" | bc)
    stock=$((i % 1000))
    weight=$(echo "scale=2; ($i % 50) / 10" | bc)
    echo "Product $i,BOMB-$(printf '%07d' $i),Auto-generated product number $i,$cat,$price,$stock,$weight" >> "$out"

    if ((i % 100000 == 0)); then
      echo "  ...${i} rows"
    fi
  done

  local size=$(du -h "$out" | cut -f1)
  echo "Done: $out ($size, 1M rows)"
}

generate_size_bomb() {
  local out="$DIR/bomb-25mb.csv"
  echo "Generating 25MB CSV → $out"
  echo "name,sku,description,category,price,stock,weight_kg" > "$out"

  local i=0
  # Pad description to ~200 chars to inflate file size quickly
  local padding="This is a deliberately long product description designed to inflate the file size beyond the twenty megabyte upload limit for stress testing purposes only"

  while [ "$(stat -f%z "$out" 2>/dev/null || stat -c%s "$out" 2>/dev/null)" -lt 26214400 ]; do
    i=$((i + 1))
    echo "Oversized Product $i,SIZE-$(printf '%07d' $i),$padding $i,Electronics,$((i % 100)).99,$((i % 500)),1.5" >> "$out"

    if ((i % 50000 == 0)); then
      local size=$(du -h "$out" | cut -f1)
      echo "  ...$size"
    fi
  done

  local size=$(du -h "$out" | cut -f1)
  echo "Done: $out ($size)"
}

case "${1:-all}" in
  rows) generate_row_bomb ;;
  size) generate_size_bomb ;;
  all)  generate_row_bomb; generate_size_bomb ;;
  *)    echo "Usage: $0 [rows|size|all]"; exit 1 ;;
esac
