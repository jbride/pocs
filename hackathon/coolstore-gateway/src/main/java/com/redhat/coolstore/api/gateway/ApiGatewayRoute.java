package com.redhat.coolstore.api.gateway;

import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.jackson.ListJacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.redhat.coolstore.api.gateway.model.Inventory;
import com.redhat.coolstore.api.gateway.model.Product;
import com.redhat.coolstore.api.gateway.model.ShoppingCart;

@Component
public class ApiGatewayRoute extends RouteBuilder {
	private static final Logger LOG = LoggerFactory.getLogger(ApiGatewayRoute.class);
	
	@Autowired
	private Environment env;
	
    @Override
    public void configure() throws Exception {
    	try {
    		getContext().setTracing(Boolean.parseBoolean(env.getProperty("ENABLE_TRACER", "false")));	
		} catch (Exception e) {
			LOG.error("Failed to parse the ENABLE_TRACER value: {}", env.getProperty("ENABLE_TRACER", "false"));
		}
    	
        JacksonDataFormat productFormatter = new ListJacksonDataFormat();
        productFormatter.setUnmarshalType(Product.class);

        restConfiguration().component("servlet")
            .bindingMode(RestBindingMode.auto).apiContextPath("/api-docs")
            .contextPath("/api").apiProperty("host", "")
            .apiProperty("api.title", "CoolStore Microservice API Gateway")
            .apiProperty("api.version", "1.0.0")
            .apiProperty("api.description", "The API of the gateway which fronts the various backend microservices in the CoolStore")
            .apiProperty("api.contact.name", "Red Hat Developers")
            .apiProperty("api.contact.email", "developers@redhat.com")
            .apiProperty("api.contact.url", "https://developers.redhat.com");

        rest("/products/").description("Product Catalog Service")
            .produces(MediaType.APPLICATION_JSON_VALUE)

        // Handle CORS Pre-flight requests
        .verb("OPTIONS", "/").route().id("productOptions").end()
        //.options("/").route().id("productsOptions").end()
        .endRest()
        .get("/").description("Get product catalog").outType(Product.class)
            .route().id("productRoute")
                .setBody(simple("null"))
                .removeHeaders("CamelHttp*")
                .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                .setHeader(Exchange.HTTP_URI, simple("http://{{env:CATALOG_ENDPOINT:catalog:8080}}/products"))
                .hystrix().id("Product Service")
                    .hystrixConfiguration()
                        .executionTimeoutInMilliseconds(5000).circuitBreakerSleepWindowInMilliseconds(10000)
                    .end()
                    .to("http4://DUMMY").log(LoggingLevel.INFO, "QUERY CATALOG SERVICE ENDPOINT")
                .onFallback()
                    .to("direct:productFallback").log(LoggingLevel.WARN, "FALLING BACK CATALOG QUERY, MAY BE CATALOG ENDPOINT IS DOWN!")
                .end()
                .choice().when(body().isNull()).to("direct:productFallback").log(LoggingLevel.WARN, "FALLING BACK CATALOG QUERY, CATALOG DOES NOT CONTAIN ANY PRODUCT!").end()
                .unmarshal(productFormatter).log(LoggingLevel.INFO, "GOT PRODUCTS, ABOUT TO ENRICH CONTENT WITH INVENTORY: [" + body() + "]")
                .split(body()).parallelProcessing()
                .enrich("direct:inventory", new InventoryEnricher()).log(LoggingLevel.INFO, "RESPONDING PRODUCTS WITH INVENTORY")
            .end()
        .endRest();

        from("direct:productFallback")
                .id("ProductFallbackRoute")
                .transform()
                .constant(Collections.singletonList(new Product("0", "Unavailable Product", "Unavailable Product", 0, null)))
                .marshal().json(JsonLibrary.Jackson, List.class);

        from("direct:inventory")
            .id("inventoryRoute")
            .setHeader("itemId", simple("${body.itemId}"))
            .setBody(simple("null"))
            .removeHeaders("CamelHttp*")
            .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
            .setHeader(Exchange.HTTP_URI, simple("http://{{env:INVENTORY_ENDPOINT:inventory:8080}}/api/inventory/${header.itemId}"))
            .hystrix().id("Inventory Service")
                .hystrixConfiguration()
                    .executionTimeoutInMilliseconds(5000).circuitBreakerSleepWindowInMilliseconds(10000)
                .end()
                .to("http4://DUMMY2").log(LoggingLevel.INFO, simple("QUERY INVENTORY SERVICE ENDPOINT for item: ${header.itemId}").toString())
            .onFallback()
                .to("direct:inventoryFallback").log(LoggingLevel.WARN, "FALLING BACK INVENTORY QUERY, MAY BE INVENTORY ENDPOINT IS DOWN!")
            .end()
            .choice().when(body().isNull()).to("direct:inventoryFallback").log(LoggingLevel.WARN, "FALLING BACK INVENTORY QUERY, RESPONSE IS NULL!").end()
            .setHeader("CamelJacksonUnmarshalType", simple(Inventory.class.getName()))
            .unmarshal().json(JsonLibrary.Jackson, Inventory.class);

        from("direct:inventoryFallback")
                .id("inventoryFallbackRoute")
                .transform()
                .constant(new Inventory("0", 0, "Local Store", "http://redhat.com"))
                .marshal().json(JsonLibrary.Jackson, Inventory.class);

        rest("/cart/").description("Personal Shopping Cart Service")
            .produces(MediaType.APPLICATION_JSON_VALUE)

            // Handle CORS Preflight requests
            .verb("OPTIONS", "/{cartId}").route().id("getCartOptionsRoute").end().endRest()
            //.options("/{cartId}").route().id("getCartOptionsRoute").end().endRest()
            .verb("OPTIONS", "/checkout/{cartId}").route().id("checkoutCartOptionsRoute").end().endRest()
            //.options("/checkout/{cartId}").route().id("checkoutCartOptionsRoute").end().endRest()
            .verb("OPTIONS", "/{cartId}/{tmpId}").route().id("cartSetOptionsRoute").end().endRest()
            //.options("/{cartId}/{tmpId}").route().id("cartSetOptionsRoute").end().endRest()
            .verb("OPTIONS", "/{cartId}/{itemId}/{quantity}").route().id("cartAddDeleteOptionsRoute").end().endRest()
            //.options("/{cartId}/{itemId}/{quantity}").route().id("cartAddDeleteOptionsRoute").end().endRest()
            .verb("OPTIONS", "/new-cart/{itemId}/{quantity}").route().id("cartAddOptionsRoute").end().endRest()
            
            .post("/new-cart/{itemId}/{quantity}").description("Create a new cart with a given product and quantity")
            	.param().name("itemId").type(RestParamType.path).description("The ID of the item to add").dataType("string").endParam()
                .param().name("quantity").type(RestParamType.path).description("The number of items to add").dataType("integer").endParam()
                .outType(ShoppingCart.class)
                .route().id("addCartRoute")
                .hystrix().id("Cart Service (Add Cart)")
                    .hystrixConfiguration()
                        .executionTimeoutInMilliseconds(5000).circuitBreakerSleepWindowInMilliseconds(10000)
                    .end()
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.POST)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/cart/cart/newcart/${header.itemId}/${header.quantity}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest()

            .post("/checkout/{cartId}").description("Finalize shopping cart and process payment")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .outType(ShoppingCart.class)
                .route().id("checkoutRoute")
                .hystrix().id("Cart Service (Checkout Cart)")
                    .hystrixConfiguration()
                        .executionTimeoutInMilliseconds(5000).circuitBreakerSleepWindowInMilliseconds(10000)
                    .end()
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.POST)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/cart/cart/checkout/${header.cartId}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest()

            .get("/{cartId}").description("Get the current user's shopping cart content")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .outType(ShoppingCart.class)
                .route().id("getCartRoute")
                .hystrix().id("Cart Service (Get Cart)")
                    .hystrixConfiguration()
                        .executionTimeoutInMilliseconds(5000).circuitBreakerSleepWindowInMilliseconds(10000)
                    .end()
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/cart/cart/${header.cartId}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest()

            .delete("/{cartId}/{itemId}/{quantity}").description("Delete items from current user's shopping cart")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .param().name("itemId").type(RestParamType.path).description("The ID of the item to delete").dataType("string").endParam()
                .param().name("quantity").type(RestParamType.path).description("The number of items to delete").dataType("integer").endParam()
                .outType(ShoppingCart.class)
                .route().id("deleteFromCartRoute")
                .hystrix().id("Cart Service (Delete Cart)")
                    .hystrixConfiguration()
                        .executionTimeoutInMilliseconds(5000).circuitBreakerSleepWindowInMilliseconds(10000)
                    .end()
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.DELETE)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/api/cart/${header.cartId}/${header.itemId}/${header.quantity}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest()

            .post("/{cartId}/{itemId}/{quantity}").description("Add items from current user's shopping cart")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .param().name("itemId").type(RestParamType.path).description("The ID of the item to add").dataType("string").endParam()
                .param().name("quantity").type(RestParamType.path).description("The number of items to add").dataType("integer").endParam()
                .outType(ShoppingCart.class)
                .route().id("addToCartRoute")
                .hystrix().id("Cart Service (Add To Cart)")
                    .hystrixConfiguration()
                        .executionTimeoutInMilliseconds(5000).circuitBreakerSleepWindowInMilliseconds(10000)
                    .end()
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.POST)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/api/cart/${header.cartId}/${header.itemId}/${header.quantity}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest()

            .post("/{cartId}/{tmpId}").description("Transfer temp shopping items to user's cart")
                .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
                .param().name("tmpId").type(RestParamType.path).description("The ID of the temp cart to transfer").dataType("string").endParam()
                .outType(ShoppingCart.class)
                .route().id("setCartRoute")
                .hystrix().id("Cart Service (Set Cart)")
                    .hystrixConfiguration()
                        .executionTimeoutInMilliseconds(5000).circuitBreakerSleepWindowInMilliseconds(10000)
                    .end()
                    .removeHeaders("CamelHttp*")
                    .setBody(simple("null"))
                    .setHeader(Exchange.HTTP_METHOD, HttpMethods.POST)
                    .setHeader(Exchange.HTTP_URI, simple("http://{{env:CART_ENDPOINT:cart:8080}}/api/cart/${header.cartId}/${header.tmpId}"))
                    .to("http4://DUMMY")
                .onFallback()
                    // TODO: improve fallback
                    .transform().constant(null)
                .end()
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
            .endRest();

    }

    private class InventoryEnricher implements AggregationStrategy {
        public Exchange aggregate(Exchange original, Exchange resource) {

            // Add the discovered availability to the product and set it back
            Product p = original.getIn().getBody(Product.class);
            Inventory i = resource.getIn().getBody(Inventory.class);
            p.setAvailability(i);
            original.getOut().setBody(p);
            return original;

        }
    }
}