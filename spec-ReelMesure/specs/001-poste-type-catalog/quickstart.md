# PosteType Catalog — Quickstart Guide

This guide walks through the catalog API from setup to basic usage.

## 0. Verify Seed Data

After the application starts, verify the seed has been applied:

```bash
psql -h localhost -U postgres -d sageLine_db -c "SELECT poste_type, COUNT(*) FROM poste_measure_catalog WHERE active GROUP BY 1;"
```

Expected: TEST_FONCTIONNEL (6), WIFI_CONDUIT (16), ACC (15)

## 1. List All Measures (Any Role)

```bash
curl -X GET "http://localhost:8089/api/poste-catalog" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

## 2. Create Measure (ADMIN_IT/CHEF_SECTEUR Only)

```bash
curl -X POST "http://localhost:8089/api/poste-catalog/measures" \
  -H "Authorization: Bearer <ADMIN_IT_JWT>" \
  -H "Content-Type: application/json" \
  -d '{"posteType":"TEST_FONCTIONNEL","measureCode":"NEW_CODE","measureLabel":"New","category":"POWER","defaultUnit":"dBm","defaultLowerBound":-20,"defaultUpperBound":10,"mandatory":true,"displayOrder":1}'
```

## 3. View Swagger UI

Visit: http://localhost:8089/swagger-ui.html

All API endpoints documented under "poste-catalog" tag.
