# CSV Import Test Files

Test files for validating all 9 security defenses in the CSV import pipeline.

## File → Defense Matrix

| File | Defense Tested | Expected Result |
|------|---------------|-----------------|
| `valid.csv` | Baseline | All 5 rows imported successfully |
| `xss-attack.csv` | [1] XSS stripping | HTML/script tags stripped, products imported clean |
| `sql-injection.csv` | [3] Parameterized queries | SQL patterns neutralized, safe values stored |
| `formula-injection.csv` | [4] Formula prefix stripping | `=`, `+`, `-`, `@`, `\|` prefixes removed |
| `mixed-attacks.csv` | [1][3][4] + [2] threat reporting | Multiple attack types detected, all neutralized |
| `malformed-data.csv` | Validation logic | Missing fields → errors; bad types → rejected |
| `duplicate-skus.csv` | `ON CONFLICT (sku) DO NOTHING` | First occurrence imported, duplicates skipped |
| `fake-binary.csv` | [9] Magic byte validation | Rejected: "File appears to be binary" |
| `wrong-extension.txt` | [5] File type validation | Rejected: "Only .csv files are accepted" |
| `empty-file.csv` | Edge case | 0 rows processed |
| `header-only.csv` | Edge case | 0 rows processed |
| `long-strings.csv` | Buffer/field length stress | Imported (1000-char strings) |
| `unicode-encoding.csv` | Encoding edge cases | UTF-8 characters preserved |
| `bomb-1m-rows.csv`* | [7] Row limit (100K max) | Only first 100K rows imported |
| `bomb-25mb.csv`* | [6] File size limit (20MB max) | Rejected: "File too large" |

*Generated files — run `./generate-bomb.sh` to create (not committed to git).

## How to Run

1. Start the app: `bb dev`
2. Login as admin (admin / admin123)
3. Go to **Import Products** page
4. Upload each file and verify the expected result
5. Use **Clear All Products** between tests for a clean slate

## Generate Large Test Files

```bash
cd docs/test-imports

# Generate both (1M rows + 25MB)
./generate-bomb.sh

# Or individually
./generate-bomb.sh rows   # → bomb-1m-rows.csv (~55MB, 1M rows)
./generate-bomb.sh size   # → bomb-25mb.csv (>20MB)
```

## Defense Reference

| # | Defense | Location |
|---|---------|----------|
| 1 | XSS — strip HTML/script tags | `csv_import.clj:strip-html` |
| 2 | Threat reporting — log detected attacks | `csv_import.clj:detect-threats` |
| 3 | SQL injection — parameterized queries | `csv_import.clj:insert-product!` |
| 4 | Formula injection — strip `=+\-@\|` prefixes | `csv_import.clj:strip-formula-prefix` |
| 5 | File type — .csv extension only | `handlers/products.clj:import-csv` |
| 6 | File size — 20MB max | `handlers/products.clj:import-csv` |
| 7 | Row limit — 100K max | `csv_import.clj:max-rows` |
| 8 | Nil file check | `handlers/products.clj:import-csv` |
| 9 | Magic byte validation — detect binary files | `handlers/products.clj:text-file?` |
