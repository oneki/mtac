package net.oneki.mtac.core.util.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.oneki.mtac.core.util.query.Filter;
import net.oneki.mtac.core.util.query.FilterCriteria;
import net.oneki.mtac.core.util.query.QueryContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.core.util.cache.ResourceRegistry;
import net.oneki.mtac.core.util.exception.BusinessException;
import net.oneki.mtac.core.util.introspect.ResourceDesc;
import net.oneki.mtac.core.util.introspect.ResourceField;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Filter {
  private static final Pattern filterFilterCriteriaPattern = Pattern.compile(
      "\\s*((?:(?:[a-zA-Z$_][a-zA-Z0-9$_.]*)\\s+(not\\s+)?(?:eq|gt|gte|lt|lte|like|i_like|in|sw|ew|is)\\s+(?:'(?:[^\\\\']|\\\\.)*'|\"(?:[^\\\\\"]|\\\\.)*\"|(?:[^\\s\"';]+)))|(?:(?:and|or)?\\s*\\(.*\\)))\\s*;?");
  private static final Pattern filterCriteriaPattern = Pattern.compile(
      "^\\s*([a-zA-Z$_][a-zA-Z0-9$_.]*)\\s+(not\\s+)?(eq|gt|gte|lt|lte|like|i_like|in|sw|i_sw|ew|i_ew|is)\\s+(?:'((?:[^\\\\']|\\\\.)*)'|\"((?:[^\\\\\"]|\\\\.)*)\"|((?:[^\\s\"';]+)))\\s*$");
  private static final Pattern filterPattern = Pattern.compile("^\\s*(and|or)?\\s*\\((.*)\\)\\s*$");

  public static enum Operator {
    AND("AND"), OR("OR");

    public String label = "";

    // Constructeur
    Operator(String label) {
      this.label = label;
    }

  }

  private QueryContext context;
  private ResourceDesc type;
  @Builder.Default
  private Operator operator = Operator.AND;
  @Builder.Default
  private List<Filter> subFilters = new ArrayList<>();
  @Builder.Default
  private List<FilterCriteria> criterias = new ArrayList<>();

  /**
   *
   * @param key:   something like and(firstname eq "john";or(lastname like
   *               "doe";lastname starts_with "michael"))
   * @param value: the value to search
   * @return Filter
   */
  public static Filter fromRest(ResourceDesc type, String filter, QueryContext context) {
    if (filter == null || filter.equals("")) {
      return null;
    }
    Filter result = asFilter(type, filter, context);
    if (result == null) {
      result = Filter.builder().type(type).context(context).build();
      result.addCriteria(asFilterCriteria(type, filter, context));
    }
    return result;
  }

  public LinkedHashMap<String, FilterCriteria> getRelations() {
    return this.context.getFilterRelations();
  }

  private static Filter asFilter(ResourceDesc type, String filter, QueryContext context) {
    Matcher matcher = filterPattern.matcher(filter);
    if (!matcher.find())
      return null;

    var result = Filter.builder().type(type).context(context).build();
    Operator operator = Operator.AND;
    String group1 = matcher.group(1);
    if (group1 != null && group1.equals("or")) {
      operator = Operator.OR;
    }
    result.setOperator(operator);

    if (matcher.group(2) != null) {
      populateFilter(type, result, matcher.group(2), context);
    }
    return result;
  };

  private static FilterCriteria asFilterCriteria(ResourceDesc type, String filterCriteria,
      QueryContext context) {
    Matcher matcher = filterCriteriaPattern.matcher(filterCriteria);
    if (!matcher.find()) {
      throw new BusinessException("INVALID_FILTER", filterCriteria + " is a not a valid filter");
    }

    FilterCriteria.Operator operator = null;
    boolean not = false;
    if (matcher.group(2) != null) {
      not = true;
    }

    for (FilterCriteria.Operator op : FilterCriteria.Operator.values()) {
      if (op.rest.equals(matcher.group(3))) {
        operator = op;
      }
    }

    String value = null;
    for (int i = 4; i <= 6; i++) {
      if (matcher.group(i) != null) {
        value = matcher.group(i);
        break;
      }
    }

    if (operator == null) {
      throw new BusinessException("INVALID_FILTER_OPERATOR", matcher.group(2) + " is a not a valid filter operator");
    }

    if (matcher.group(1) == null) {
      throw new BusinessException("INVALID_FILTER_FIELD", "Field is required in a filter expression");
    }

    if (value == null) {
      throw new BusinessException("INVALID_FILTER_VALUE", "Value is required in a filter expression");
    }

    String field = matcher.group(1);

    return asFilterCriteria(type, operator, field, value, not, context);
  };

  public static FilterCriteria asFilterCriteria(ResourceDesc resourceDesc, FilterCriteria.Operator operator,
      String field, String value, boolean not, QueryContext context) {
    // Example: field = "label" or field = "description" or field =
    // "memberOf.description" or field = "memberOf.label"
    if (field.contains(".")) {
      ArrayList<String> parts = new ArrayList<>();
      parts.addAll(Arrays.asList(field.split("\\.")));

      if (resourceDesc != null) {
        var fieldName = parts.remove(0);
        ResourceField fieldDef = resourceDesc.getField(fieldName);
        // We have to create a "relation" temporary table only if
        // - the field is not of type JSON (a JSON is a complex object but it's not a
        // relation and the data is saved with the resource)
        // - the field is not embedded (similar to the JSON but only the first level is
        // saved with the resource -> we need to check if there is not a sub-relation)
        // - the field of the relation is not id / label / type (these infos are inside
        // the resource ref which is saved with resource)
        if (fieldDef != null && fieldDef.isRelation()) {
          String subField = String.join(".", parts);
          if (!Arrays.asList("id", "label", "schema").contains(subField.toLowerCase())) {
            // filter inside a relation -> need to create a relationFilter (which will lead
            // to an additional SQL request)
            String relationTable = populateRelation(fieldDef.getType(), operator, subField, not, value, context);
            return FilterCriteria.builder()
                .resourceDesc(resourceDesc)
                .operator(FilterCriteria.Operator.EQUALS)
                .field(fieldDef.getLabel() + ".id")
                .value(relationTable + ".id")
                .negation(false)
                .join(true)
                .relation(false)
                .build();
          }
        }
      }
    }
    return new FilterCriteria(resourceDesc, operator, field, value, not, false, false);
  };

  private static String populateRelation(String typeLabel, FilterCriteria.Operator operator, String field, boolean not,
      String value, QueryContext context) {
    ResourceDesc resourceDesc = ResourceRegistry.getResourceDesc(typeLabel);
    if (field.contains(".")) {
      ArrayList<String> parts = new ArrayList<>();
      parts.addAll(Arrays.asList(field.split("\\.")));

      var fieldName = parts.remove(0);
      ResourceField fieldDef = resourceDesc.getField(fieldName);
      // We have to create a "relation" temporary table only if
      // - the field is not of type JSON (a JSON is a complex object but it's not a
      // relation and the data is saved with the resource)
      // - the field is not embedded (similar to the JSON but only the first level is
      // saved with the resource -> we need to check if there is not a sub-relation)
      // - the field of the relation is not id / label / type (these infos are inside
      // the resource ref which is saved with resource)
      if (fieldDef != null && fieldDef.isRelation()) {
        String subField = String.join(".", parts);
        if (!Arrays.asList("id", "label", "schema").contains(subField.toLowerCase())) {
          // filter inside a relation -> need to create a relationFilter (which will lead
          // to an additional SQL request)
          String relationTable = populateRelation(fieldDef.getType(), operator, subField, not, value, context);
          return context.addFilterRelation(new FilterCriteria(resourceDesc, FilterCriteria.Operator.EQUALS,
              fieldDef.getLabel() + ".id", relationTable + ".id", false, true, false));
        }
      }
    }

    return context.addFilterRelation(FilterCriteria.builder()
        .resourceDesc(resourceDesc)
        .operator(operator)
        .field(field)
        .value(value)
        .negation(not)
        .join(false)
        .relation(false)
        .build());
  }

  private static void populateFilter(ResourceDesc type, Filter filter, String filterComponents,
      QueryContext context) {
    // we need to split the components of the filter components
    // they are separated by semi colon but it's possible to inner filter are also
    // separated by semi colons
    // so we need to detect the "external" semi colon
    int ignore = 0; // if the counter is 0, we don't ignore semi colon
    String pass = null;
    int start = 0;
    List<String> components = new ArrayList<>();
    for (int i = 0; i < filterComponents.length(); i++) {
      char currentChar = filterComponents.charAt(i);

      if (currentChar == '"' && (i == 0 || filterComponents.charAt(i - 1) != '\\')) {
        if (pass == null) {
          pass = "\""; // start of string
        } else if (pass.equals("\"")) {
          pass = null; // end of string
        }
      }
      if (currentChar == '\'' && (i == 0 || filterComponents.charAt(i - 1) != '\\')) {
        if (pass == null) {
          pass = "\'"; // start of string
        } else if (pass.equals("'")) {
          pass = null; // end of string
        }
      }
      if (pass == null) {
        if (currentChar == '(') {
          ignore++;
        } else if (currentChar == ')') {
          ignore--;
        }

        if ((currentChar == ';' || i == filterComponents.length() - 1) && ignore == 0) {
          if (start < i) {
            components.add(filterComponents.substring(start, currentChar == ';' ? i : i + 1));
          }
          start = i + 1;
        }
      }
    }

    for (String component : components) {
      Matcher matcher = filterFilterCriteriaPattern.matcher(component);
      while (matcher.find()) {
        Filter subfilter = asFilter(type, matcher.group(1), context);
        if (subfilter == null) {
          filter.addCriteria(asFilterCriteria(type, matcher.group(1), context));
        } else {
          filter.addSubfilter(subfilter);
        }
      }
    }
  }

  public void setOperator(Operator operator) {
    this.operator = operator;
  }

  public void addSubfilter(Filter filter) {
    subFilters.add(filter);
  }

  public void addCriteria(FilterCriteria criteria) {
    criterias.add(criteria);
  }

  /**
   * Convert a filter object to an SQL WHERE clause
   */
  public Map<String, String> toSql(Map<String, String> tableAliases, Map<String, Object> args) {
    Map<String, String> ret = new HashMap<>();
    String filterSql = "";
    String joinTableSql = "";
    String leftJoinTableSql = "";
    boolean first = true;
    for (int i = 0; i < criterias.size(); i++) {
      FilterCriteria criteria = criterias.get(i);
      Object value = criteria.getSqlValue();
      // In the SQL query, we'll have placeholders named :param1, :param2, etc.
      String param = "param" + (args.size() + 1);
      if (value != null) {
        args.put(param, value);
      }
      // Convert the FilterCriteria to a SQL where clause
      Map<String, String> criteriaSql = criteria.toSql(tableAliases, param, value);
      // We need to know if it's the first criteria
      // Exemple: the result is firstname='john' and lastname='doe'
      // If it's the first criteria, we don't need to add the "AND" keyword
      if (first) {
        first = false;
      } else {
        filterSql += " " + operator.label + " ";
      }
      filterSql += criteriaSql.get("where");
      joinTableSql += criteriaSql.get("table");
      leftJoinTableSql += criteriaSql.get("leftJoinTable");
    }

    // Example of subfilter
    // firstName = 'John' and (email = 'john.doe@gmail.com' or email = 'john.doe@yahoo.com')
    // The subfilter is the part inside the parenthesis
    for (int i = 0; i < subFilters.size(); i++) {
      Filter subFilter = subFilters.get(i);
      if (first) {
        first = false;
      } else {
        filterSql += " " + operator.label + " ";
      }
      Map<String, String> subFilterSql = subFilter.toSql(tableAliases, args);
      filterSql += "(" + subFilterSql.get("where") + ")";
      joinTableSql += subFilterSql.get("table");
      leftJoinTableSql += subFilterSql.get("leftJoinTable");
    }

    ret.put("where", "(" + filterSql + ")");
    ret.put("table", joinTableSql);
    ret.put("leftJoinTable", leftJoinTableSql);
    return ret;
  }
}
