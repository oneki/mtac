package net.oneki.mtac.framework.query;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import net.oneki.mtac.framework.introspect.ResourceDesc;
import net.oneki.mtac.framework.util.sql.SqlUtils;
import net.oneki.mtac.model.core.util.EntityUtils;
import net.oneki.mtac.model.core.util.exception.BusinessException;

@Data
@NoArgsConstructor
@Builder
public class FilterCriteria {
  public static enum Operator {
    GREATER("gt", ">", "<=", "#>>"), 
    GREATER_OR_EQUALS("gte", ">=", "<", "#>>"),
    LESSER("lt", "<", ">=", "#>>"), 
    LESSER_OR_EQUALS("lte", "<=", ">", "#>>"),
    EQUALS("eq", "=", "<>", "#>>"), 
    IN("in", "in", "not in", "#>>"), 
    IS("is", "is", "is not", "#>>"),
    LIKE("like", "like", "not like", "#>>"), 
    I_LIKE("i_like", "ilike", "not ilike", "#>>"),
    STARTS_WITH("sw", "like", "not like", "#>>"), 
    I_STARTS_WITH("i_sw", "ilike", "not ilike", "#>>"),
    ENDS_WITH("ew", "like", "not like", "#>>"),
    I_ENDS_WITH("i_ew", "ilike", "not ilike", "#>>");

    public String rest = "";
    public String sql = "";
    public String negationSql = "";
    public String jsonbOperator = "";

    // Constructeur
    Operator(String rest, String sql, String negationSql, String jsonbOperator) {
      this.rest = rest;
      this.sql = sql;
      this.negationSql = negationSql;
      this.jsonbOperator = jsonbOperator;
    }

  }

  private ResourceDesc resourceDesc;
  private Operator operator;
  private String field;
  private String value;
  private boolean negation;
  private boolean join;
  private boolean relation;

  public FilterCriteria(ResourceDesc resourceDesc, Operator operator, String field, String value, boolean negation, boolean join, boolean relation) {
    if (!EntityUtils.isValidField(field)) {
      throw new BusinessException(BusinessException.BUSINESS_INVALID_FIELD, "Invalid field " + field);
    }
    this.resourceDesc = resourceDesc;
    if (operator == Operator.IS && value != null && !value.toLowerCase().equals("null")) {
      operator = Operator.EQUALS;
    }
    this.operator = operator;
    this.field = field;
    this.value = value;
    this.negation = negation;
    this.join = join;
    this.relation = relation;

  }

  /**
   *
   * @param key:   something like "eq;foo" (meaning foo=)
   * @param value: the value to search
   * @return FilterCriteria
   */
  public static FilterCriteria fromRest(ResourceDesc resourceDesc, String key, String value) {
    try {
      key = URLDecoder.decode(key, "UTF-8");
      value = URLDecoder.decode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }

    String[] parts = key.split(";");
    if (parts.length == 3 || parts.length == 4) {
      boolean negation = false;
      int propIndex = 2;
      int opIndex = 1;
      if (parts.length == 4) {
        if (!parts[1].equals("not")) {
          new BusinessException(BusinessException.BUSINESS_INVALID_OPERATOR,
              "Invalid negation operator " + parts[1] + " (should be 'not')");
        }
        negation = true;
        propIndex = 3;
        opIndex = 2;
      }
      for (Operator operator : Operator.values()) {
        if (operator.rest.equals(parts[opIndex])) {
          return FilterCriteria.builder()
              .resourceDesc(resourceDesc)
              .operator(operator)
              .field(parts[propIndex])
              .value(value)
              .negation(negation)
              .join(false)
              .relation(false)
              .build();
        }
      }
      throw new BusinessException(BusinessException.BUSINESS_INVALID_OPERATOR, "Invalid operator " + parts[opIndex]);
    } else if (parts.length == 2) {
      return FilterCriteria.builder()
          .resourceDesc(resourceDesc)
          .operator(Operator.EQUALS)
          .field(parts[1])
          .value(value)
          .negation(false)
          .join(false)
          .relation(false)
          .build();
    } else {
      throw new BusinessException(BusinessException.BUSINESS_INVALID_SYNTAX, "Invalid syntax " + key);
    }
  }

  public boolean isJoinCriteria() {
    return this.join;
  }

  /**
   * Convert the FilterCriteria to a SQL where clause
   */
  public Map<String, String> toSql(Map<String, String> tableAliases, String namedParameter, Object value) {
    Map<String, String> ret = new HashMap<>();
    String where = "";
    // Example of leftOperand: {
    //   operand: "jae_table1 #>> '{firstName}'",
    //   leftJoinTable: " LEFT JOIN json_array_elements((r.content->'memberOf')::json) jae_table1 ON TRUE",
    //   table: ""
    // }
    Map<String, String> leftOperand = SqlUtils.getLeftOperand(resourceDesc, field, tableAliases, operator.jsonbOperator);
    if (value == null) {
      where += leftOperand.get("operand") + ((negation) ? " IS NOT " : " IS") + " NULL";

    } else if(join) {
      // We've created a temporary table for a relationship on which we need to filter 
      // Example: we need to filter all the groups of a user whose description is "foo" 
      //          We've generated a temporary table "table1" which contains only all the groups whose description is "foo" 
      //          We then need to make a join between r.content.memberOf[].id and table1.id
      where += "EXISTS (SELECT 1 FROM " + ((String) value).split("\\.")[0] + " WHERE " + leftOperand.get("operand") + " = " + value + ")";

    } else if(relation) {
      where += leftOperand.get("operand") + " = " + value;

    } else {
      String operatorSql = (negation) ? operator.negationSql : operator.sql;
      if (negation && operator.negationSql == null) {
        where += "NOT (";
        operatorSql = operator.sql;
      }

      where += leftOperand.get("operand");

      where += " " + operatorSql + " ";
      if (operator == Operator.IN) {
        where += "(:" + namedParameter + ")";
      } else {
        where += ":" + namedParameter;
      }

      if (negation && operator.negationSql == null) {
        where += ")";
      }
    }

    ret.put("table", leftOperand.get("table"));
    ret.put("leftJoinTable", leftOperand.get("leftJoinTable"));
    ret.put("where", where);
    return ret;
  }
 
  public Object getSqlValue() {
    String sqlField = field.substring(field.lastIndexOf('.') + 1);
    // Dates are only supported (at the moment) for create_at and updated_at fields
    Set<String> dateFields = Set.of("created_at", "updated_at");
    // We support only a subset of operators for dates
    Set<Operator> dateOperators = Set.of(Operator.IN, Operator.GREATER, Operator.GREATER_OR_EQUALS, Operator.LESSER, Operator.LESSER_OR_EQUALS, Operator.EQUALS);
    Set<Operator> numberOperators = Set.of(Operator.GREATER, Operator.GREATER_OR_EQUALS, Operator.LESSER, Operator.LESSER_OR_EQUALS);
    if (operator == Operator.IN) {
      // if the operator is IN, we convert ids to integer because ids are sometimes used for joining tables
      // We convert dates to ISO format to be compliant with values stored in postgres
      List<String> values = Arrays.asList(value.split(","));
      String k = field.substring(field.lastIndexOf('.') + 1);
      if (k.equals("id")) {
        return values.stream().map(v -> Integer.parseInt(v)).collect(Collectors.toList());
      } else if (dateFields.contains(sqlField)) {
        return values.stream().map(v -> {
          try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(v);
          } catch(ParseException e) {
            throw new BusinessException(BusinessException.BUSINESS_INVALID_SYNTAX, v + " is not a correct datetime (format is yyyy-MM-dd HH:mm:ss)");
          }
        }).collect(Collectors.toList());
      } else {
        return values;
      }
    }

    if (dateFields.contains(sqlField)) {
      if (!dateOperators.contains(operator)) {
        throw new BusinessException(BusinessException.BUSINESS_INVALID_FIELD, "Operator " + operator + " is not supported for the field " + sqlField);
      }
      try {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value);
      } catch(ParseException e) {
        throw new BusinessException(BusinessException.BUSINESS_INVALID_SYNTAX, value + " is not a correct date (format is yyyy-MM-dd HH:mm:ss)");
      }
    }

    if (operator == Operator.LIKE || operator == Operator.I_LIKE) {
      return "%" + value + "%";
    }
    if (operator == Operator.STARTS_WITH || operator == Operator.I_STARTS_WITH) {
      return value + "%";
    }
    if (operator == Operator.ENDS_WITH || operator == Operator.I_ENDS_WITH) {
      return "%" + value;
    }

    if (numberOperators.contains(operator)) {
      // If the operator is a number operator, we convert the value to an integer
      String k = field.substring(field.lastIndexOf('.') + 1);
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        return Double.parseDouble(value);
      }
    }

    if (operator == Operator.EQUALS) {
      switch (value.toLowerCase()) {
        case "true":
          // When using a GIN index (the one used for JSONB columns), the boolean must be a string (if the operator is '=')
          return "true";
        case "false":
          return "false";
        default:
          // the value is always converted ton a string exception if it's the id (which is converted to an integer)
          String k = field.substring(field.lastIndexOf('.') + 1);
          if (k.equals("id") || k.equals("link_type")) {
            try {
              return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {}

          }
          return value.toString();
      }
    }

    if (operator == Operator.IS) {
      switch (value.toLowerCase()) {
        // When the operator is "IS", the value must be null, true or false (so not a string)
        case "null":
          return null;
        case "true":
          return true;
        case "false":
          return false;
      }
    }

    return value;
  }

}
