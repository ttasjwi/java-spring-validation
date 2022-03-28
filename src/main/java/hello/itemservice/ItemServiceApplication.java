package hello.itemservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ItemServiceApplication
        // implements WebMvcConfigurer
{

    public static void main(String[] args) {
        SpringApplication.run(ItemServiceApplication.class, args);
    }

//	@Override 글로벌 설정 (모든 컨트롤러에 글로벌하게 적용)
//	public Validator getValidator() {
//		return new ItemValidator();
//	}

}
