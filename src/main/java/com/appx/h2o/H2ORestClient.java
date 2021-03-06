package com.appx.h2o;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.Future;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.appx.h2o.tasks.ParseV3Task;
import com.appx.h2o.tasks.SplitFrameV3Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;

import water.bindings.pojos.FrameKeyV3;
import water.bindings.pojos.ImportFilesV3;
import water.bindings.pojos.ParseSetupV3;
import water.bindings.pojos.ParseV3;
import water.bindings.pojos.RapidsV99;
import water.bindings.pojos.SplitFrameV3;

public class H2ORestClient {

  private final String url;
  private final Timer timer = new Timer();

  private PoolingHttpClientConnectionManager cm;
  private CloseableHttpClient client;
  private ObjectMapper mapper = new ObjectMapper();

  public H2ORestClient(final String url) {
    this.url = url;

    cm = new PoolingHttpClientConnectionManager();
    cm.setMaxTotal(200); // Increase max total connection to 200
    cm.setDefaultMaxPerRoute(20); // Increase default max connection per route
                                  // to 20
    RequestConfig reqConfig =
        RequestConfig.custom().setSocketTimeout(30 * 1000).setConnectTimeout(3 * 1000).setConnectionRequestTimeout(3 * 1000).build();

    client = HttpClients.custom().setConnectionManager(cm).setDefaultRequestConfig(reqConfig).build();

  }

  public ParseSetupV3 guessSetup(String[] source_frames) throws Exception {

    HttpPost post =
        new HttpPost(new URIBuilder(url).setPath("/3/ParseSetup").setParameter("source_frames", toArray(true, source_frames)).build());
    post.setHeader(HttpHeaders.CONTENT_TYPE, com.google.common.net.MediaType.FORM_DATA + "; charset=UTF-8");
    post.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

    List<NameValuePair> vals = new ArrayList<>();
    vals.add(new BasicNameValuePair("source_frames", toArray(true, source_frames)));
    post.setEntity(new UrlEncodedFormEntity(vals));

    HttpResponse response = client.execute(post);

    ParseSetupV3 pv3 = null;
    try {
      if (response.getStatusLine().getStatusCode() == 200) {
        pv3 = mapper.readValue(response.getEntity().getContent(), ParseSetupV3.class);
      }
    } finally {
      EntityUtils.consume(response.getEntity());
    }

    return pv3;

  }

  public ParseSetupV3 guessSetup(ImportFilesV3 importV3) throws Exception {
    ParseSetupV3 setup = guessSetup(importV3.destination_frames);
    return setup;
  }

  public ImportFilesV3 importFiles(String path) throws Exception {

    HttpGet get = new HttpGet(new URIBuilder(url).setPath("/3/ImportFiles").setParameter("path", path).build());
    get.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA + "; charset=UTF-8");
    get.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

    HttpResponse response = client.execute(get);

    ImportFilesV3 pv3 = null;
    try {
      if (response.getStatusLine().getStatusCode() == 200) {
        pv3 = mapper.readValue(response.getEntity().getContent(), ImportFilesV3.class);
      }
    } finally {
      EntityUtils.consume(response.getEntity());
    }

    return pv3;
  }

  public Future<ParseV3> parse(ParseSetupV3 p) throws Exception {

    List<NameValuePair> vals = new ArrayList<>();
    vals.add(new BasicNameValuePair("source_frames", toArray(p.source_frames)));
    vals.add(new BasicNameValuePair("chunk_size", String.valueOf(p.chunk_size)));
    vals.add(new BasicNameValuePair("destination_frame", escapePath(p.destination_frame)));
    vals.add(new BasicNameValuePair("number_columns", String.valueOf(p.number_columns)));
    vals.add(new BasicNameValuePair("column_names", toArray(true, p.column_names)));
    vals.add(new BasicNameValuePair("column_types", toArray(true, p.column_types)));
    vals.add(new BasicNameValuePair("separator", String.valueOf(p.separator)));
    vals.add(new BasicNameValuePair("check_header", String.valueOf(p.check_header)));
    vals.add(new BasicNameValuePair("delete_on_done", Boolean.TRUE.toString()));
    vals.add(new BasicNameValuePair("parse_type", p.parse_type.name()));
    vals.add(new BasicNameValuePair("single_quotes", String.valueOf(p.single_quotes)));

    HttpPost post = new HttpPost(url + "/3/Parse");
    post.setEntity(new UrlEncodedFormEntity(vals));
    post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA + "; charset=UTF-8");
    post.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

    HttpResponse response = client.execute(post);
    Thread.sleep(3000);
    ParseV3Task task = null;
    try {
      if (response.getStatusLine().getStatusCode() == 200) {

        ParseV3 pv3 = mapper.readValue(response.getEntity().getContent(), ParseV3.class);
        task = new ParseV3Task(client, url, pv3);
        timer.schedule(task, 500, 1000);

      }
    } finally {
      EntityUtils.consume(response.getEntity());
    }

    return task;
  }

  public Future<ParseV3> parse(ParseV3 p) throws Exception {

    List<NameValuePair> vals = new ArrayList<>();
    vals.add(new BasicNameValuePair("source_frames", toArray(p.source_frames)));
    vals.add(new BasicNameValuePair("chunk_size", String.valueOf(p.chunk_size)));
    vals.add(new BasicNameValuePair("destination_frame", escapePath(p.destination_frame.name)));
    vals.add(new BasicNameValuePair("number_columns", String.valueOf(p.number_columns)));
    vals.add(new BasicNameValuePair("column_names", toArray(true, p.column_names)));
    vals.add(new BasicNameValuePair("column_types", toArray(true, p.column_types)));
    vals.add(new BasicNameValuePair("separator", String.valueOf(p.separator)));
    vals.add(new BasicNameValuePair("check_header", String.valueOf(p.check_header)));
    vals.add(new BasicNameValuePair("delete_on_done", String.valueOf(p.delete_on_done)));
    vals.add(new BasicNameValuePair("parse_type", p.parse_type.name()));
    vals.add(new BasicNameValuePair("single_quotes", String.valueOf(p.single_quotes)));

    HttpPost post = new HttpPost(url + "/3/Parse");
    post.setEntity(new UrlEncodedFormEntity(vals));
    post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA + "; charset=UTF-8");
    post.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

    HttpResponse response = client.execute(post);
    Thread.sleep(3000);
    ParseV3Task task = null;
    try {
      if (response.getStatusLine().getStatusCode() == 200) {

        ParseV3 pv3 = mapper.readValue(response.getEntity().getContent(), ParseV3.class);
        task = new ParseV3Task(client, url, pv3);
        timer.schedule(task, 500, 1000);
      }
    } finally {
      EntityUtils.consume(response.getEntity());
    }

    return task;

  }

  public RapidsV99[] splitFrameV2(ParseV3 parse, Double[] ratios) throws Exception {

    HttpPost post = new HttpPost(new URIBuilder(url).setPath("/99/Rapids").build());
    post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA + "; charset=UTF-8");
    post.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

    String uuid = UUID.randomUUID().toString();
    String frameName = "(gput (" + uuid + " (h2o.runif \"" + parse.destination_frame.name + "\" #-1))";

    List<NameValuePair> vals = new ArrayList<>();
    vals.add(new BasicNameValuePair("ast", frameName));
    post.setEntity(new UrlEncodedFormEntity(vals));

    HttpResponse response = null;
    try {
      response = client.execute(post);
    } finally {
      EntityUtils.consume(response.getEntity());
    }

    if (response.getStatusLine().getStatusCode() == 200) {

      List<RapidsV99> resps = new ArrayList<>();

      for (Double r : ratios) {
        vals = new ArrayList<>();
        frameName = "(gput \"" + parse.destination_frame.name + "-" + r.toString() + "\" ([ %\"" + parse.destination_frame.name + "\"" + " (l %"
            + uuid + " #" + r.toString() + ") \"null\"))";
        vals.add(new BasicNameValuePair("ast", frameName));
        post.setEntity(new UrlEncodedFormEntity(vals));
        response = client.execute(post);

        try {
          if (response.getStatusLine().getStatusCode() == 200) {

            RapidsV99 pv3 = mapper.readValue(response.getEntity().getContent(), RapidsV99.class);
            resps.add(pv3);

          }
        } finally {
          EntityUtils.consume(response.getEntity());
        }
      }

      HttpDelete delete = new HttpDelete(new URIBuilder(url).setPath("/3/Frames/" + uuid).addParameter("frame_id", uuid).build());
      client.execute(delete);
      
      return resps.toArray(new RapidsV99[] {});
    }

    return new RapidsV99[] {};
  }

  public Future<SplitFrameV3> splitFrame(ParseV3 parse, Double[] ratios) throws Exception {

    HttpPost post = new HttpPost(new URIBuilder(url).setPath("/3/SplitFrame").build());
    post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA + "; charset=UTF-8");
    post.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

    List<NameValuePair> vals = new ArrayList<>();
    vals.add(new BasicNameValuePair("dataset", toArray(true, parse.destination_frame.name)));
    vals.add(new BasicNameValuePair("ratios", toArray(ratios)));

    List<String> frames = new ArrayList<>();
    for (Double r : ratios) {
      frames.add(parse.destination_frame.name + "-" + r.toString());
    }

    vals.add(new BasicNameValuePair("destination_frames", toArray(true, frames.toArray(new String[] {}))));
    post.setEntity(new UrlEncodedFormEntity(vals));

    HttpResponse response = client.execute(post);
    SplitFrameV3Task task = null;
    try {
      if (response.getStatusLine().getStatusCode() == 200) {

        SplitFrameV3 pv3 = mapper.readValue(response.getEntity().getContent(), SplitFrameV3.class);
        task = new SplitFrameV3Task(client, url, pv3);
        timer.schedule(task, 500, 1000);
      }
    } finally {
      EntityUtils.consume(response.getEntity());
    }

    return task;
  }

  private String toArray(FrameKeyV3[] keys) {

    List<String> frames = new ArrayList<>();

    for (FrameKeyV3 k : keys) {
      frames.add(k.name);
    }

    return toArray(true, frames.toArray(new String[] {}));
  }

  private String toArray(Double[] values) {

    List<String> frames = new ArrayList<>();

    for (Double k : values) {
      frames.add(k.toString());
    }

    return toArray(false, frames.toArray(new String[] {}));
  }

  private String escapePath(String path) {
    return "\"" + path + "\"";
  }

  private String toArray(boolean escape, String... strings) {
    StringBuffer buffer = new StringBuffer("[");

    int leng = strings.length;
    int i = 1;
    for (String s : strings) {

      if (escape) {
        buffer.append(escapePath(s));
      } else {
        buffer.append(s);
      }
      if (i < leng) {
        buffer.append(",");
      }
      i++;

    }
    buffer.append("]");
    return buffer.toString();
  }
}
