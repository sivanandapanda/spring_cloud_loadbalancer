package com.example.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer.Factory;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.client.loadbalancer.reactive.Response;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import static com.example.client.ClientApplication.call;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @Bean
    WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    @LoadBalanced
    WebClient.Builder builder() {
        return WebClient.builder();
    }

    static Flux<Greeting> call(WebClient webClient, String url) {
		return webClient.get().uri(url).retrieve().bodyToFlux(Greeting.class);
    }

}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Greeting {
    private String greetings;
}

@Component
@Log4j2
class ConfiguredWebClientRunner {
    ConfiguredWebClientRunner(WebClient http) {
        call(http, "http://api/greetings").subscribe(greeting -> log.info("configured: " + greeting.toString()));
    }
}

@Component
@Log4j2
class WebClientRunner {

    WebClientRunner(Factory<ServiceInstance> serviceInstanceFactory) {
        var filter = new ReactorLoadBalancerExchangeFilterFunction(serviceInstanceFactory);
        var http = WebClient.builder()
                .filter(filter).build();

        call(http, "http://api/greetings").subscribe(greeting -> log.info("filter: " + greeting.toString()));
    }
}

@Component
@Log4j2
class ReactiveLoadBalanceFactoryRunner {
    ReactiveLoadBalanceFactoryRunner(Factory<ServiceInstance> serviceInstanceFactory) {
        ReactiveLoadBalancer<ServiceInstance> api = serviceInstanceFactory.getInstance("api");

        var http = WebClient.builder().build();

        Flux<Response<ServiceInstance>> chosen = Flux.from(api.choose());

        chosen.map(responseServiceInstance -> {
            ServiceInstance server = responseServiceInstance.getServer();
            var url = "http://" + server.getHost() + ":" + server.getPort() + "/greetings";
            log.info(url);
            return url;
        }).flatMap(url -> call(http, url)).subscribe(greeting -> log.info("manual: " + greeting.toString()));
    }
}