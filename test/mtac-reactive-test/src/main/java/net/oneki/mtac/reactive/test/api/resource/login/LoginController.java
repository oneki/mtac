package net.oneki.mtac.reactive.test.api.resource.login;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.resource.iam.identity.user.UserService;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class LoginController {
    private final UserService userService;

    @GetMapping("/api/oauth2/userinfo")
    public Mono<Map<String, Object>> userinfo() throws Exception {
        return userService.userinfo();
    }
}
