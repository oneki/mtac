package net.oneki.mtac.framework.query;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.framework.cache.ResourceRegistry;
import net.oneki.mtac.framework.introspect.ResourceDesc;
import net.oneki.mtac.framework.query.Filter.Operator;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.resource.Resource;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Query {
  private Integer tenant;
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
          var currentFilter = query.getFilter();
          var urlFilter = Filter.fromRest(type, value, queryContext);
          var subFilters = new ArrayList<Filter>();
          subFilters.add(urlFilter);
          if (currentFilter == null) {
            query.setFilter(Filter.builder()
                .operator(Operator.AND)
                .subFilters(subFilters)
                .context(queryContext)
                .build());
          } else {
            currentFilter.addSubfilter(urlFilter);
          }
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
          query.setTenant(Integer.parseInt(value));
          break;
        case "search":
          var filter = query.getFilter();
          var criteria = FilterCriteria.builder()
              .field("label")
              .operator(net.oneki.mtac.framework.query.FilterCriteria.Operator.I_LIKE)
              .value(value)
              .build();
          var criterias = new ArrayList<FilterCriteria>();
          criterias.add(criteria);
          if (filter == null) {
            query.setFilter(Filter.builder()
                .operator(Operator.AND)
                .context(queryContext)
                .criterias(criterias)
                .build());
          } else {
            filter.getCriterias().add(criteria);
          }
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
