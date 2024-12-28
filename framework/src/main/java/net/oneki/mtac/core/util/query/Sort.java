package net.oneki.mtac.core.util.query;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.core.util.EntityUtils;
import net.oneki.mtac.core.util.cache.ResourceRegistry;
import net.oneki.mtac.core.util.exception.BusinessException;
import net.oneki.mtac.core.util.introspect.ResourceDesc;
import net.oneki.mtac.core.util.introspect.ResourceField;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Sort {

  private List<SortCriteria> criterias;
  private QueryContext context;

  public static Sort fromRest(ResourceDesc type, String sort, String sortOrder, QueryContext context) {
    if (sort == null || sort.equals("")) {
      return null;
    }
    try {
      sort = URLDecoder.decode(sort, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    String[] sortCriterias = sort.split(";");
    // if (sorts.length > 1) {
    // throw new BusinessException(BusinessException.BUSINESS_INVALID_SYNTAX,
    // "Do not yet support multiple sorts at once");
    // }

    List<SortCriteria> criterias = new ArrayList<>();

    for (String sortCriteria : sortCriterias) {
      String[] sortBys = sortCriteria.split(",");
      if (!EntityUtils.isValidField(sortBys[0])) {
        throw new BusinessException(BusinessException.BUSINESS_INVALID_FIELD, "Invalid field " + sortBys[0]);
      }
      String sortBy = sortBys[0];
      if (sortBys.length > 1) {
        sortOrder = (sortBys[1].equals("desc")) ? "desc" : "asc";
      } else if (sortOrder == null) {
        sortOrder = "asc";
      } else {
        sortOrder = (sortOrder.equals("desc")) ? "desc" : "asc";
      }
      criterias.add(asSortCriteria(type, sortBy, sortOrder, context));
    }
    return Sort.builder().criterias(criterias).context(context).build();
  }

  public static SortCriteria asSortCriteria(ResourceDesc type, String sortBy, String sortOrder,
      QueryContext context) {
    if (sortBy.contains(".")) {
      ArrayList<String> parts = new ArrayList<>();
      parts.addAll(Arrays.asList(sortBy.split("\\.")));

      if (type != null) {
        var fieldName = parts.remove(0);
        ResourceField fieldDef = type.getField(fieldName);
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
            // sort inside a relation -> need to create a relationFilter (which will lead to
            // an additional SQL request)
            String relationTable = populateRelation(fieldDef.getType(), subField, sortOrder, context);
            return new SortCriteria(type, null, sortOrder, new Relation(fieldDef.getLabel(), relationTable));
          }
        }
      }
    }
    return new SortCriteria(type, sortBy, sortOrder, null);
  };

  private static String populateRelation(String typeLabel, String sortBy, String sortOrder, QueryContext context) {
    ResourceDesc type = ResourceRegistry.getResourceDesc(typeLabel);
    if (sortBy.contains(".")) {
      ArrayList<String> parts = new ArrayList<>();
      parts.addAll(Arrays.asList(sortBy.split("\\.")));

      var fieldName = parts.remove(0);
      ResourceField fieldDef = type.getField(fieldName);
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
          String relationTable = populateRelation(fieldDef.getType(), subField, sortOrder, context);
          return context.addSortRelation(
              new SortCriteria(type, null, sortOrder, new Relation(fieldDef.getLabel(), relationTable)));
        }
      }
    }
    return context.addSortRelation(new SortCriteria(type, sortBy, sortOrder, null));
  }

  public LinkedHashMap<String, SortCriteria> getRelations() {
    return this.context.getSortRelations();
  }

  public List<SortCriteria> getSortCriterias() {
    if (criterias == null) {
      return new ArrayList<>();
    }
    return criterias;
  }
}
