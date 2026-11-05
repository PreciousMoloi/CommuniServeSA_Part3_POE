# CommuniServe SA REST API – Postman Test Plan

Run the server first. Use the base URL `http://localhost:3000` for a local computer test.

## 1. Health
GET `/health`
Expected: HTTP 200 and `status: ok`.

## 2. Register
POST `/api/auth/register`
JSON:
```json
{"name":"Test User","email":"test@example.com","password":"Password123"}
```
Expected: HTTP 201, token and user object.

## 3. Login
POST `/api/auth/login`
JSON:
```json
{"email":"test@example.com","password":"Password123"}
```
Expected: HTTP 200 and 24-hour JWT token.

## 4. Services
GET `/api/services?q=plumb&radius=10`
Expected: filtered JSON list.

## 5. Protected service submission
POST `/api/services`
Header: `Authorization: Bearer <token>`
JSON:
```json
{"title":"Test Plumbing","category":"Plumber","description":"Test listing","phone":"0100000000","type":"Service"}
```
Expected: HTTP 201 and `verified:false`.

## 6. Review
POST `/api/reviews/s1`
Header: `Authorization: Bearer <token>`
JSON:
```json
{"rating":5,"text":"Excellent local service."}
```
Expected: HTTP 201.

## 7. Profile/settings
GET `/api/users/profile`
PUT `/api/users/profile`
Header: `Authorization: Bearer <token>`
JSON:
```json
{"language":"isiZulu","notifications":true,"radius":25}
```
Expected: updated profile with the selected settings.

Capture screenshots of successful requests/responses for the final evidence pack.
