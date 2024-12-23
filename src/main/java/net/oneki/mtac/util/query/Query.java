package net.oneki.mtac.util.query;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.oneki.mtac.util.query.Filter;
import net.oneki.mtac.util.query.Query;
import net.oneki.mtac.util.query.QueryContext;
import net.oneki.mtac.util.query.Sort;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.model.entity.Resource;
import net.oneki.mtac.util.cache.ResourceRegistry;
import net.oneki.mtac.util.exception.BusinessException;
import net.oneki.mtac.util.introspect.ResourceDesc;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Query {
  private String tenant;
  private Integer limit;
  private Integer offset;
  private boolean noLink;
  private boolean noPublic;
  private boolean forceTenant;
  private Sort sort;
  private Filter filter;
  private boolean serviceFirst;
  private Set<String> fields;
  private Set<String> relations;

  public static <T extends Resource> Query fromRest(Map<String, String> params, Class<T> resultContentClass) {
    var schemaLabel = ResourceRegistry.getSchemaByClass(resultContentClass);
    return fromRest(schemaLabel, params);
  }

  public static Query fromRest(String schemaLabel, Map<String, String> params) {
    return fromRest(schemaLabel, params, 100, 1000);
  }

  public static Query fromRest(String schemaLabel, Map<String, String> params, Integer defaultLimit, Integer maxLimit) {
    ResourceDesc type = null;
    if (schemaLabel != null) {
      type = ResourceRegistry.getResourceDesc(schemaLabel);
    }

    Query query = new Query();
    String sortOrder = null;
    String sortBy = null;
    QueryContext queryContext = new QueryContext();

    for (var entry : params.entrySet()) {
      var k = entry.getKey();
      var value = entry.getValue();
      switch (k) {
        case "filter":
          query.setFilter(Filter.fromRest(type, value, queryContext));
          break;
        case "sortBy":
          try {
            sortBy = URLDecoder.decode(value, "UTF-8");
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
          }
        case "sortOrder":
          sortOrder = (value.equals("desc")) ? "desc" : "asc";
          break;
        case "limit":
          var limit = Integer.parseInt(value);
          if (limit > maxLimit) {
            throw new BusinessException("LIMIT_TOO_HIGH", "the query param limit cannot be higher than " + maxLimit);
          } 
          query.setLimit(Integer.parseInt(value));
          break;
        case "offset":
          query.setOffset(Integer.parseInt(value));
          break;
        case "noLink":
          query.setNoLink((value == null) ? true : Boolean.parseBoolean(value));
          break;
        case "noPublic":
          query.setNoPublic((value == null) ? true : Boolean.parseBoolean(value));
          break;
        case "forceTenant":
          query.setForceTenant((value == null) ? true : Boolean.parseBoolean(value));
          break;
        case "fields":
          query.setFields(new HashSet<String>(Arrays.asList(value.split(","))));
          break;
        case "relations":
          query.setRelations(new HashSet<String>(Arrays.asList(value.split(","))));
          break;
        case "tenant":
          query.setTenant(value);
          break;
      }

    }

    if (query.getLimit() == null) {
      query.setLimit(defaultLimit);
    }

    if (query.getOffset() == null) {
      query.setOffset(0);
    }

    if (sortBy == null) {
      sortBy = "id";
      sortOrder = "asc";
    }
    query.setSort(Sort.fromRest(type, sortBy, sortOrder, queryContext));

    return query;
  }
}
