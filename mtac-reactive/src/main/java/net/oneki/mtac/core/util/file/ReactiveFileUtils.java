package net.oneki.mtac.core.util.file;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveFileUtils {
    // Read file to Flux<String> lines
    public static Flux<String> readFileToFlux(String filePath) throws IOException{
        return  readFileToFlux( new BufferedReader(new InputStreamReader(new FileInputStream(filePath))));
    }

    public static Flux<String> readFileToFlux(BufferedReader bufferedReader) {
        return Flux.using(
                () -> bufferedReader,
                reader -> Flux.fromStream(reader.lines()),

                // resource cleanup function closes the FileReader when the Flux is complete
                reader -> Mono.fromRunnable(() -> {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        // Handle the exception (e.g., log it)
                        throw new RuntimeException("Error closing file reader", e);
                    }
                }));
    }

    // Read file to Mono<String> content
    public static Mono<String> readFileToOneLineMono(String filePath) throws IOException{
        return readFileToFlux(filePath)
                .reduce("", (acc, line) -> acc + line.trim() + " ")
                .map(content -> content.trim()); // Trim the final content to remove trailing newline
    }

}
