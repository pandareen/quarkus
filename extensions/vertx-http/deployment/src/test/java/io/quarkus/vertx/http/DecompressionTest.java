package io.quarkus.vertx.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.SnappyFrameEncoder;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

public class DecompressionTest {
    private static final String APP_PROPS = "" +
            "quarkus.http.enable-decompression=true\n";

    private static final String LONG_STRING = IntStream.range(0, 1000).mapToObj(i -> "Hello World;")
            .collect(Collectors.joining());

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource(new StringAsset(APP_PROPS), "application.properties")
                    .addClasses(CompressionTest.BeanRegisteringRouteUsingObserves.class));

    @Test
    public void test() throws Exception {
        var input = LONG_STRING.getBytes(StandardCharsets.UTF_8);
        var bout = new ByteArrayOutputStream(input.length);
        var gzip = new GZIPOutputStream(bout);
        gzip.write(input, 0, input.length);
        gzip.close();
        var compressed = bout.toByteArray();
        bout.close();

        // RestAssured is aware of quarkus.http.root-path
        // If this changes then please modify quarkus-azure-functions-http maven archetype to reflect this
        // in its test classes
        RestAssured.given()
                .header("content-encoding", "gzip")
                .body(compressed)
                .post("/echo").then().statusCode(200)
                .body(Matchers.equalTo(LONG_STRING));

        RestAssured.given()
                .body(LONG_STRING)
                .post("/echo").then().statusCode(200)
                .body(Matchers.equalTo(LONG_STRING));
    }

    @Test
    public void testInvalidGzipReturns400() {
        RestAssured.given()
                .header("content-encoding", "gzip")
                .body("not gzip".getBytes(StandardCharsets.UTF_8))
                .post("/echo")
                .then()
                .statusCode(400)
                .body(Matchers.containsString("Invalid compressed"));
    }

    @Test
    public void testSnappyFramedRoundTrip() {
        byte[] input = LONG_STRING.getBytes(StandardCharsets.UTF_8);
        byte[] compressed = snappyFramed(input);

        RestAssured.given()
                .header("content-encoding", "snappy")
                .body(compressed)
                .post("/echo").then().statusCode(200)
                .body(Matchers.equalTo(LONG_STRING));
    }

    private static byte[] snappyFramed(byte[] input) {
        EmbeddedChannel encoder = new EmbeddedChannel(new SnappyFrameEncoder());
        encoder.writeOutbound(Unpooled.wrappedBuffer(input));
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ByteBuf part;
        while ((part = encoder.readOutbound()) != null) {
            try {
                int n = part.readableBytes();
                byte[] chunk = new byte[n];
                part.readBytes(chunk);
                bout.write(chunk);
            } finally {
                part.release();
            }
        }
        encoder.finishAndReleaseAll();
        return bout.toByteArray();
    }

}
