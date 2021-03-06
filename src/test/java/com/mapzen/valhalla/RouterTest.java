package com.mapzen.valhalla;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.fest.assertions.api.Assertions.assertThat;

public class RouterTest {
    @Captor
    @SuppressWarnings("unused")
    ArgumentCaptor<Router.Callback> callback;

    @Captor
    @SuppressWarnings("unused")
    ArgumentCaptor<Route> route;

    @Captor
    @SuppressWarnings("unused")
    ArgumentCaptor<Integer> statusCode;

    Router validRouter;

    MockWebServer server;

    @Before
    public void setup() throws Exception {
        server = new MockWebServer();
        MockitoAnnotations.initMocks(this);
        double[] loc = new double[] {1.0, 2.0};
        validRouter = new Router().setLocation(loc).setLocation(loc);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void shouldHaveDefaultEndpoint() throws Exception {
        assertThat(validRouter.getEndpoint()).startsWith("http://valhalla.mapzen.com/");
    }

    @Test
    public void shouldSetEndpoint() throws Exception {
        validRouter.setEndpoint("http://testing.com");
        assertThat(validRouter.getEndpoint()).startsWith("http://testing.com");
    }

    @Test
    public void shouldDefaultToCar() throws Exception {
        assertThat(validRouter.getJSONRequest().costing).contains("auto");
    }

    @Test
    public void shouldSetToCar() throws Exception {
        validRouter.setDriving();
        assertThat(validRouter.getJSONRequest().costing).contains("auto");
    }

    @Test
    public void shouldSetToBike() throws Exception {
        validRouter.setBiking();
        assertThat(validRouter.getJSONRequest().costing).contains("bicycle");
    }

    @Test
    public void shouldSetToFoot() throws Exception {
        validRouter.setWalking();
        assertThat(validRouter.getJSONRequest().costing).contains("pedestrian");
    }

    @Test
    public void shouldClearLocations() throws Exception {
        double[] loc1 = { 1.0, 2.0 };
        double[] loc2 = { 3.0, 4.0 };
        double[] loc3 = { 5.0, 6.0 };
        Router router = new Router()
                .setLocation(loc1)
                .setLocation(loc2);
        router.clearLocations();
        router.setLocation(loc2);
        router.setLocation(loc3);
        JSON json = router.getJSONRequest();
        assertThat(json.locations[0].lat).doesNotContain("1.0");
        assertThat(json.locations[0].lat).contains("3.0");
        assertThat(json.locations[1].lat).contains("5.0");
    }

    @Test(expected=MalformedURLException.class)
    public void shouldThrowErrorWhenNoLocation() throws Exception {
        new Router().getJSONRequest();
    }

    @Test(expected=MalformedURLException.class)
    public void shouldThrowErrorWhenOnlyOneLocation() throws Exception {
        new Router().setLocation(new double[]{1.0, 1.0}).getJSONRequest();
    }

    @Test
    public void shouldAddLocations() throws Exception {
        double[] loc1 = { 1.0, 2.0 };
        double[] loc2 = { 3.0, 4.0 };
        JSON json = new Router()
                .setLocation(loc1)
                .setLocation(loc2)
                .getJSONRequest();
        assertThat(json.locations[0].lat).contains("1.0");
        assertThat(json.locations[0].lon).contains("2.0");
        assertThat(json.locations[1].lat).contains("3.0");
        assertThat(json.locations[1].lon).contains("4.0");
    }

    @Test
    public void shouldGetRoute() throws Exception, JSONException {
        startServerAndEnqueue(new MockResponse().setBody(getFixture("brooklyn")));
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                String endpoint = server.getUrl("").toString();
                Router.Callback callback = Mockito.mock(Router.Callback.class);
                Router router = new Router()
                        .setEndpoint(endpoint)
                        .setLocation(new double[]{40.659241, -73.983776})
                        .setLocation(new double[]{40.671773, -73.981115});
                router.setCallback(callback);
                router.fetch();
                Mockito.verify(callback).success(route.capture());
                assertThat(route.getValue().foundRoute()).isTrue();
            }
        });
    }

    @Test
    public void shouldGetError() throws Exception {
        startServerAndEnqueue(new MockResponse().setResponseCode(500));
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Router.Callback callback = Mockito.mock(Router.Callback.class);
                String endpoint = server.getUrl("").toString();
                Router router = new Router()
                        .setEndpoint(endpoint)
                        .setLocation(new double[]{40.659241, -73.983776})
                        .setLocation(new double[]{40.671773, -73.981115});
                router.setCallback(callback);
                router.fetch();
                Mockito.verify(callback).failure(statusCode.capture());
                assertThat(statusCode.getValue()).isEqualTo(500);
            }
        });
    }

    @Test
    public void shouldGetNotFound() throws Exception {
        startServerAndEnqueue(new MockResponse().setResponseCode(404));
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Router.Callback callback = Mockito.mock(Router.Callback.class);
                String endpoint = server.getUrl("").toString();
                Router router = new Router()
                        .setEndpoint(endpoint)
                        .setLocation(new double[]{40.659241, -73.983776})
                        .setLocation(new double[]{40.671773, -73.981115});
                router.setCallback(callback);
                router.fetch();
                Mockito.verify(callback).failure(statusCode.capture());
                assertThat(statusCode.getValue()).isEqualTo(404);
            }
        });
    }

    @Test
    public void shouldGetRouteNotFound() throws Exception {
        startServerAndEnqueue(new MockResponse().setBody(getFixture("unsuccessful")));
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Router.Callback callback = Mockito.mock(Router.Callback.class);
                String endpoint = server.getUrl("").toString();
                Router router = new Router()
                        .setEndpoint(endpoint)
                        .setLocation(new double[]{40.659241, -73.983776})
                        .setLocation(new double[]{40.671773, -73.981115});
                router.setCallback(callback);
                router.fetch();
                Mockito.verify(callback).failure(statusCode.capture());
                assertThat(statusCode.getValue()).isEqualTo(207);
            }
        });
    }

    @Test
    public void shouldStoreRawRoute() throws Exception {
        startServerAndEnqueue(new MockResponse().setBody(getFixture("brooklyn")));
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                String endpoint = server.getUrl("").toString();
                Router.Callback callback = Mockito.mock(Router.Callback.class);
                Router router = new Router()
                        .setEndpoint(endpoint)
                        .setLocation(new double[] { 40.659241, -73.983776 })
                        .setLocation(new double[] { 40.671773, -73.981115 });
                router.setCallback(callback);
                router.fetch();
                Mockito.verify(callback).success(route.capture());
                assertThat(route.getValue().getRawRoute().toString())
                        .isEqualTo(getFixture("brooklyn"));
            }
        });
    }

    private void startServerAndEnqueue(MockResponse response) throws Exception {
        server.enqueue(response);
        server.play();
    }

    private static String urlEncode(String raw) throws UnsupportedEncodingException {
        return URLEncoder.encode(raw, "utf-8");
    }

    public static String getFixture(String name) {
        String basedir = System.getProperty("user.dir");
        File file = new File(basedir + "/src/test/fixtures/" + name + ".route");
        String fixture = "";
        try {
            fixture = Files.toString(file, Charsets.UTF_8);
        } catch (Exception e) {
            fixture = "not found";
        }
        return fixture;
    }
}
