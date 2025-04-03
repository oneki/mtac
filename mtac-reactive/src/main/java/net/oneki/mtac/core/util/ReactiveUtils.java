package net.oneki.mtac.core.util;

import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveUtils {
  public static <T> Flux<T> monoToFlux(Mono<T> mono) {
    return mono.map(e -> List.of(e)).flatMapMany(Flux::fromIterable);
  }
}
