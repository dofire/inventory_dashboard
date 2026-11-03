package com.dofire.sv_vehicle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "POSTGRES_PASSWORD=test-only",
        "logging.file.name=build/test-logs/sv_vehicle.json"
})
class SvVehicleApplicationTests {

	@Test
	void contextLoads() {
	}

}
