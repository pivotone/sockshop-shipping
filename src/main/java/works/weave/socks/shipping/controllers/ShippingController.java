package works.weave.socks.shipping.controllers;

import com.rabbitmq.client.Channel;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Extension;
import io.swagger.annotations.ExtensionProperty;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import works.weave.socks.shipping.entities.HealthCheck;
import works.weave.socks.shipping.entities.Result;
import works.weave.socks.shipping.entities.Shipment;
import works.weave.socks.shipping.utils.ResultUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "shipping apis")
@RestController
public class ShippingController {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @ApiOperation(value = "get shipping",
            extensions = @Extension(properties = {@ExtensionProperty(name = "x-forward-compatible-marker", value = "0")})
    )
    @GetMapping(value = "/shipping")
    public Result getShipping() {
        return ResultUtil.success();
    }

    @ApiOperation(value = "get shipping with id",
            extensions = @Extension(properties = {@ExtensionProperty(name = "x-forward-compatible-marker", value = "0")})
    )
    @GetMapping(value = "/shipping/{id}")
    public Result getShippingById(@PathVariable String id) {
        return ResultUtil.success("GET Shipping Resource with id: " + id, null);
    }

    @ApiOperation(value = "create shipping and use mq",
            extensions = @Extension(properties = {@ExtensionProperty(name = "x-forward-compatible-marker", value = "0")})
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/shipping")
    public
    @ResponseBody
    Result postShipping(@RequestBody Shipment shipment) {
        System.out.println("Adding shipment to queue...");
        try {
            rabbitTemplate.convertAndSend("shipping-task", shipment);
        } catch (Exception e) {
            System.out.println("Unable to add to queue (the queue is probably down). Accepting anyway. Don't do this " +
                    "for real!");
        }
        return ResultUtil.success(shipment);
    }

    @ApiOperation(value = "get service's health",
            extensions = @Extension(properties = {@ExtensionProperty(name = "x-forward-compatible-marker", value = "0")})
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping(path = "/health")
    public
    @ResponseBody
    Result getHealth() {
        Map<String, List<HealthCheck>> map = new HashMap<String, List<HealthCheck>>();
        List<HealthCheck> healthChecks = new ArrayList<HealthCheck>();
        Date dateNow = Calendar.getInstance().getTime();

        HealthCheck rabbitmq = new HealthCheck("shipping-rabbitmq", "OK", dateNow);
        HealthCheck app = new HealthCheck("shipping", "OK", dateNow);

        try {
            this.rabbitTemplate.execute(new ChannelCallback<String>() {
                @Override
                public String doInRabbit(Channel channel) throws Exception {
                    Map<String, Object> serverProperties = channel.getConnection().getServerProperties();
                    return serverProperties.get("version").toString();
                }
            });
        } catch ( AmqpException e ) {
            rabbitmq.setStatus("err");
        }

        healthChecks.add(rabbitmq);
        healthChecks.add(app);

        map.put("health", healthChecks);
        return ResultUtil.success(map);
    }
}
