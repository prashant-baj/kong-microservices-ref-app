# Swagger Documentation Setup

## Overview
Swagger/OpenAPI documentation has been successfully configured for all microservices in the Kong microservices reference application. This enables comprehensive API documentation and interactive testing through Swagger UI.

## Accessing Swagger UI

Once the services are running, you can access the Swagger UI for each service at the following URLs:

### Product Service
- **URL**: http://localhost:8081/swagger-ui.html
- **API Docs JSON**: http://localhost:8081/v3/api-docs
- **Port**: 8081

### Order Service
- **URL**: http://localhost:8083/swagger-ui.html
- **API Docs JSON**: http://localhost:8083/v3/api-docs
- **Port**: 8083

### Inventory Service
- **URL**: http://localhost:8082/swagger-ui.html
- **API Docs JSON**: http://localhost:8082/v3/api-docs
- **Port**: 8082

## Features Implemented

### 1. OpenAPI Configuration
Each service has an `OpenApiConfig` class that customizes the OpenAPI metadata:
- **Service Name**: Descriptive title for each API
- **Description**: Clear explanation of the service's purpose
- **Version**: 1.0.0
- **Contact Information**: Team details with email and URL
- **License**: Apache 2.0

**Locations**:
- `services/product-service/src/main/java/com/lab/product/config/OpenApiConfig.java`
- `services/order-service/src/main/java/com/lab/order/config/OpenApiConfig.java`
- `services/inventory-service/src/main/java/com/lab/inventory/config/OpenApiConfig.java`

### 2. Controller Annotations
Each controller method has been annotated with:
- **@Operation**: Summary and detailed description of what the endpoint does
- **@ApiResponses**: Response codes (200, 201, 400, 404, 409, 500) with descriptions
- **@ApiResponse**: Details for each response including content type and schema
- **@Parameter**: Documentation for path and query parameters with examples
- **@Tag**: Grouping related endpoints by resource

**Controllers Updated**:
- ProductController (`/api/products`)
- OrderController (`/api/orders`)
- InventoryController (`/api/inventory`)

### 3. DTO Schema Annotations
All request and response DTOs have been enhanced with:
- **@Schema**: Descriptive documentation for each data class
- **Field-level annotations**: Individual field descriptions with realistic examples

**DTOs Updated**:
- Product Service: `CreateProductRequest`, `ProductResponse`
- Order Service: `CreateOrderRequest`, `OrderResponse`, `OrderLineItemRequest`, `LineItemResponse`
- Inventory Service: `AddStockRequest`, `ReserveStockRequest`, `ReservationResponse`, `StockItemResponse`

### 4. Application Configuration
Each service's `application.yml` has been updated with Swagger configuration:
```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    operationsSorter: method      # Sort operations by HTTP method
    tagsSorter: alpha              # Sort tags alphabetically
```

## API Endpoints Documentation

### Product Service

#### Create Product (POST /api/products)
- Creates a new product with name, description, price, and category
- Returns: 201 Created with product details

#### Get Product (GET /api/products/{id})
- Retrieves a specific product by ID
- Returns: 200 OK with product details

#### Get All Products (GET /api/products)
- Retrieves all available products
- Returns: 200 OK with array of products

### Order Service

#### Create Order (POST /api/orders)
- Creates a new order with customer name and line items
- Returns: 201 Created with order details

#### Get Order (GET /api/orders/{id})
- Retrieves a specific order by ID
- Returns: 200 OK with order and line items

#### Get All Orders (GET /api/orders)
- Retrieves all available orders
- Returns: 200 OK with array of orders

### Inventory Service

#### Add Stock (POST /api/inventory/stock)
- Adds inventory stock for a product
- Returns: 201 Created with stock item details

#### Reserve Stock (POST /api/inventory/reservations)
- Reserves stock for an order
- Returns: 201 Created with reservation details

#### Cancel Reservation (DELETE /api/inventory/reservations/{id})
- Cancels an existing stock reservation
- Returns: 200 OK with updated reservation details

#### Get Stock (GET /api/inventory/stock/{productId})
- Retrieves stock information for a product
- Returns: 200 OK with stock item details

## Usage Examples

### Try It Out in Swagger UI
1. Open the Swagger UI in your browser (e.g., http://localhost:8081/swagger-ui.html)
2. Click on an endpoint to expand it
3. Click the "Try it out" button
4. Fill in any required parameters
5. Click "Execute" to test the endpoint
6. View the response and response headers

### Example Request Body (Create Product)
```json
{
  "name": "Laptop",
  "description": "High-performance laptop for professionals",
  "price": 999.99,
  "category": "Electronics"
}
```

### Example Response (Product)
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Laptop",
  "description": "High-performance laptop for professionals",
  "price": 999.99,
  "category": "Electronics",
  "createdAt": "2026-02-10T10:30:00Z",
  "updatedAt": "2026-02-10T10:30:00Z"
}
```

## Dependencies Used

- **springdoc-openapi-starter-webmvc-ui**: Version 2.5.0
  - Provides OpenAPI 3.0 support for Spring Boot
  - Includes Swagger UI for interactive API documentation
  - Auto-generates API documentation from annotations

## Best Practices Implemented

1. **Comprehensive Documentation**: Every endpoint is documented with clear descriptions
2. **Response Examples**: All DTOs include realistic example values
3. **Consistent Naming**: Operations are sorted by method, tags alphabetically
4. **Validation Documentation**: Constraints are documented on request parameters
5. **Error Responses**: All possible HTTP status codes are documented
6. **Type Information**: Schema annotations provide complete type information

## Maintenance

When adding new endpoints:
1. Use `@Operation` to describe the endpoint
2. Use `@ApiResponses` and `@ApiResponse` for all possible status codes
3. Use `@Parameter` to document path/query parameters
4. Update DTOs with `@Schema` annotations
5. Provide realistic examples in schema descriptions

## Testing the Documentation

To verify the Swagger documentation is working:
1. Start all services: `docker-compose up` or run services individually
2. Open each service's Swagger UI in a browser
3. Verify all endpoints are listed with descriptions
4. Test an endpoint using the "Try it out" feature
5. Verify response codes and body schema information

