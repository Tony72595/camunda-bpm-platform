package com.camunda.bpm.engine.rest;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class TemplateParser {

  public static void main(String[] args) throws ParseException, IOException, TemplateException {

    if (args.length != 3) {
      throw new RuntimeException("Must provide two arguments: <source template directory> <main template> <output directory>");
    }

    String sourceDirectory = args[0];
    String mainTemplate = args[1];
    String outputFile = args[2];

    Configuration cfg = new Configuration();

    cfg.setDirectoryForTemplateLoading(new File(sourceDirectory));
    cfg.setDefaultEncoding("UTF-8");

    Template template = cfg.getTemplate(mainTemplate);

    Map<String, Object> templateData = createTemplateData();

    try (StringWriter out = new StringWriter()) {

      template.process(templateData, out);

      // format json with Gson
      String jsonString = out.getBuffer().toString();
      String formattedJson = formatJsonString(jsonString);

      File outFile = new File(outputFile);
      FileUtils.forceMkdir(outFile.getParentFile());
      Files.write(outFile.toPath(), formattedJson.getBytes());
    }
  }

  private static Map<String, Object> createTemplateData() {
    Map<String, Object> templateData = new HashMap<>();

    String version = TemplateParser.class.getPackage().getImplementationVersion();

    // docsVersion = 7.X.Y
    templateData.put("cambpmVersion", version);

    if (version.contains("SNAPSHOT")) {
      templateData.put("docsVersion", "develop");
    } else {
      // docsVersion = 7.X
      templateData.put("docsVersion", version.substring(0, version.lastIndexOf(".")));
    }
    return templateData;
  }

  private static String formatJsonString(String jsonString) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    JsonParser jsonParser = new JsonParser();
    JsonElement json = jsonParser.parse(jsonString);
    String formattedJson = gson.toJson(json);

    return formattedJson;
  }

}
