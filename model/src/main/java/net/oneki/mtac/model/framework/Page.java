package net.oneki.mtac.model.framework;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Paginated response wrapper containing a subset of results and pagination metadata")
public class Page<T> {
    @Schema(description = "List of resources in the current page")
    List<T> data;

    @Schema(description = "Whether there are more resources available beyond this page", example = "true")
    boolean hasNext;

    @Schema(description = "The offset used for this page (number of resources skipped)", example = "0")
    Integer offset;

    @Schema(description = "The maximum number of resources returned per page", example = "100")
    Integer limit;
}
