package net.oneki.mtac.util.query;

import java.util.LinkedHashMap;

import net.oneki.mtac.util.query.FilterCriteria;
import net.oneki.mtac.util.query.SortCriteria;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.oneki.mtac.util.sql.SqlUtils;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QueryContext {

  @Builder.Default private LinkedHashMap<String, FilterCriteria> filterRelations = new LinkedHashMap<>();
  @Builder.Default private LinkedHashMap<String, SortCriteria> sortRelations = new LinkedHashMap<>();


  public String addFilterRelation(FilterCriteria filterCriteria) {
    String table = SqlUtils.generateUniqueTableName("filter");
    filterRelations.put(table, filterCriteria);

    return table;
  }

  public String addSortRelation(SortCriteria sortCriteria) {
    String table = SqlUtils.generateUniqueTableName("sort");
    sortRelations.put(table, sortCriteria);

    return table;
  }

  public LinkedHashMap<String, FilterCriteria> getFilterRelations() {
    return filterRelations;
  }

  public LinkedHashMap<String, SortCriteria> getSortRelations() {
    return sortRelations;
  }


}
