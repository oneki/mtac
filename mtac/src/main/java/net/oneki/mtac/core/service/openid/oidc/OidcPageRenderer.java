package net.oneki.mtac.core.service.openid.oidc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.model.core.config.MtacProperties;

/**
 * Renders the pages hosted by the OpenID provider (login, mfa, consent, logout,
 * error) from HTML templates of the classpath folder
 * {@code mtac.openid.server.templates-path}.
 *
 * <p>
 * The syntax is a small subset of Mustache: <code>{{name}}</code> is replaced by
 * the HTML-escaped value, <code>{{#name}}...{{/name}}</code> is kept only when
 * the value is present and <code>{{^name}}...{{/name}}</code> only when it is
 * absent. <code>{{*name}}...{{/name}}</code> repeats the block for each item of
 * the list {@code name}; inside it, every name refers to a field of the item
 * (sections included). There is no way to output a value unescaped.
 */
@Service
@RequiredArgsConstructor
public class OidcPageRenderer {
  private static final Pattern SECTION = Pattern.compile("\\{\\{([#^])(\\w+)\\}\\}(.*?)\\{\\{/\\2\\}\\}", Pattern.DOTALL);
  private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\w+)\\}\\}");
  private static final Pattern LIST = Pattern.compile("\\{\\{\\*(\\w+)\\}\\}(.*?)\\{\\{/\\1\\}\\}", Pattern.DOTALL);
  private static final Pattern TAG = Pattern.compile("\\{\\{([#^/]?)(\\w+)\\}\\}");

  private final MtacProperties mtacProperties;
  private final ConcurrentHashMap<String, String> templates = new ConcurrentHashMap<>();

  public String render(String name, Map<String, String> model) {
    return render(name, model, Map.of());
  }

  /**
   * @param lists items of the list blocks of the template, each item being the
   *              values of its fields
   */
  public String render(String name, Map<String, String> model, Map<String, List<Map<String, String>>> lists) {
    var template = templates.computeIfAbsent(name, this::load);
    var values = new HashMap<String, String>(model);

    // lists first, on the template only: each item gets a copy of the block whose names
    // are made unique (list__index__field), then the values go through the passes below
    var listBlocks = LIST.matcher(template);
    var withLists = new StringBuilder();
    while (listBlocks.find()) {
      var listName = listBlocks.group(1);
      var items = lists != null ? lists.getOrDefault(listName, List.of()) : List.<Map<String, String>>of();
      var expanded = new StringBuilder();
      for (var index = 0; index < items.size(); index++) {
        var prefix = listName + "__" + index + "__";
        var tags = TAG.matcher(listBlocks.group(2));
        while (tags.find()) {
          tags.appendReplacement(expanded,
              Matcher.quoteReplacement("{{" + tags.group(1) + prefix + tags.group(2) + "}}"));
        }
        tags.appendTail(expanded);
        items.get(index).forEach((field, value) -> values.put(prefix + field, value));
      }
      listBlocks.appendReplacement(withLists, Matcher.quoteReplacement(expanded.toString()));
    }
    listBlocks.appendTail(withLists);
    template = withLists.toString();

    // sections next, on the template only: values are never interpreted
    var sections = SECTION.matcher(template);
    var withSections = new StringBuilder();
    while (sections.find()) {
      var value = values.get(sections.group(2));
      var present = value != null && !value.isEmpty();
      var keep = "#".equals(sections.group(1)) ? present : !present;
      sections.appendReplacement(withSections, Matcher.quoteReplacement(keep ? sections.group(3) : ""));
    }
    sections.appendTail(withSections);

    var variables = VARIABLE.matcher(withSections);
    var result = new StringBuilder();
    while (variables.find()) {
      var value = values.get(variables.group(1));
      variables.appendReplacement(result, Matcher.quoteReplacement(escape(value)));
    }
    variables.appendTail(result);
    return result.toString();
  }

  private String load(String name) {
    var path = mtacProperties.getOpenId().getServer().getTemplatesPath() + "/" + name + ".html";
    try (var input = new ClassPathResource(path).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("OpenID page template not found on the classpath: " + path, e);
    }
  }

  public static String escape(String value) {
    if (value == null) {
      return "";
    }
    var result = new StringBuilder(value.length() + 16);
    for (var i = 0; i < value.length(); i++) {
      var c = value.charAt(i);
      switch (c) {
        case '&' -> result.append("&amp;");
        case '<' -> result.append("&lt;");
        case '>' -> result.append("&gt;");
        case '"' -> result.append("&quot;");
        case '\'' -> result.append("&#39;");
        default -> result.append(c);
      }
    }
    return result.toString();
  }
}
