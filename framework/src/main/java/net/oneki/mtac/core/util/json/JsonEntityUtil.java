package net.oneki.mtac.core.util.json;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import net.oneki.mtac.core.util.ByteUtil;

/**
 * JsonUtil
 */
public class JsonEntityUtil {
    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new EntityModule(mapper));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static String object2Json(Object obj) {
        return object2Json(obj, false);
    }

    public static String object2Json(Object obj, boolean prettyPrint) {
        try {
            if (prettyPrint) {
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
            } else {
                return mapper.writeValueAsString(obj);
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed: " + e.getMessage(), e);
        }
    }

    public static <T> T json2Object(String json, Class<T> clazz) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization failed: " + e.getMessage(), e);
        }
    }

    public static <T> T bytes2Object(byte[] bytes, Class<T> clazz) {
        var json = new String(bytes, StandardCharsets.UTF_8);
        return json2Object(json, clazz);
    }

    public static byte[] object2Bytes(Object object) {
        var json = object2Json(object);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static <T> List<T> json2List(String json, Class<T> clazz) {
        try {
            return mapper.readerForListOf(clazz).readValue(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed: " + e.getMessage(), e);
        }
    }

    public static <T> Set<T> json2Set(String json, Class<T> clazz) {
        var list = json2List(json, clazz);
        return new HashSet<T>(list);
    }

    public static <T> T object2implementation(Object obj, Class<T> clazz) {
        return mapper.convertValue(obj, clazz);
    }

    public static <T> T file2Object(String filepath, Class<T> clazz) {
        Resource resource = new ClassPathResource(filepath, JsonUtil.class);
        return file2Object(resource, clazz);
    }

    public static <T> T file2Object(Path filepath, Class<T> clazz) {
        return file2Object(new PathResource(filepath), clazz);
    }

    public static <T> T file2Object(File file, Class<T> clazz) {
        FileSystemResource resource = new FileSystemResource(file);
        return file2Object(resource, clazz);
    }

    public static <T> T file2Object(Resource resource, Class<T> clazz) {
        try {
            BufferedReader buffer = new BufferedReader(new InputStreamReader(resource.getInputStream()));
            String json = buffer.lines().map(String::trim).reduce("", (x, y) -> x.concat(' ' + y));
            return json2Object(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static String file2String(String filepath) {
        try {
            Resource resource = new ClassPathResource(filepath, JsonUtil.class);
            BufferedReader buffer = new BufferedReader(new InputStreamReader(resource.getInputStream()));
            String json = buffer.lines().map(String::trim).reduce("", (x, y) -> x.concat(' ' + y));
            return json;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static void object2File(String path, Object object) throws IOException {
        object2File(path, object, false);
    }

    public static void object2File(Path path, Object object) throws IOException {
        object2File(path, object, false);
    }

    public static void object2File(Path path, Object object, boolean prettyPrint) throws IOException {
        object2File(path.normalize().toString(), object, prettyPrint);
    }

    public static void object2File(String path, Object object, boolean prettyPrint) throws IOException {
        var json = object2Json(object, prettyPrint);
        try (var writer = Files.newBufferedWriter(Paths.get(path), StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            writer.write(json);
        }
    }

    public static void object2GzipFile(String path, Object object) throws IOException {
        FileOutputStream fStream = null;
        GZIPOutputStream zStream = null;

        try {
            fStream = new FileOutputStream(path);
            zStream = new GZIPOutputStream(new BufferedOutputStream(fStream));
            mapper.writeValue(zStream, object);
        } finally {
            if (zStream != null) {
                zStream.flush();
                zStream.close();
            }
            if (fStream != null) {
                fStream.flush();
                fStream.close();
            }
        }
    }

    public static String object2GzipBase64String(Object object) throws IOException {
        var jsonBytes = object2Json(object).getBytes(StandardCharsets.UTF_8);
        byte[] gzipToBase64 = ByteUtil.encodeToBase64(ByteUtil.encodeToGZIP(jsonBytes));
        return new String(gzipToBase64);
    }

    public static <T> T gzipBase64String2Object(String base64String, Class<T> clazz) throws IOException {
        byte[] gzipBytes = ByteUtil.decodeFromBase64(base64String.getBytes());
        byte[] jsonBytes = ByteUtil.decodeFromGZIP(gzipBytes);
        return json2Object(new String(jsonBytes, StandardCharsets.UTF_8), clazz);
    }

    public static void object2Outputstream(Object obj, OutputStream os)
            throws JsonGenerationException, JsonMappingException, IOException {
        ObjectWriter writer = mapper.writer();
        writer.writeValue(os, obj);
    }

    

}
