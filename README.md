
# java-spring-validation

우아한형제들 김영한 님의 인프런 강의 '스프링 MVC 2편 - 백엔드 웹 개발 활용 기술'의 4,5장, "검증" 부분을 따라치면서 정리하는 Repository

---

## V1
- 수동으로 예외 메시지를 작성하여 Map에 추가 후 Model에 넣어 다시 Form 반환하기

<details>
<summary>상세 설명</summary>
<div markdown="1">

### 컨트롤러에 검증 로직 추가
```java
    @PostMapping("/add")
    public String addItem(@ModelAttribute Item item, RedirectAttributes redirectAttributes, Model model) {

        // 검증 오류 결과를 보관
        Map<String, String> errors = new HashMap<>();

        // 검증 로직
        if (!StringUtils.hasText(item.getItemName())) {
            errors.put("itemName", "상품 이름은 필수 입니다.");
        }

        if (item.getPrice()==null || item.getPrice() < 1000 || item.getPrice() > 1_000_000) {
            errors.put("price", "가격은 1,000 ~ 1,000,000까지 허용됩니다.");
        }

        if (item.getQuantity() == null || item.getQuantity() > 9999) {
            errors.put("quantity", "수량은 최대 9,999까지 허용됩니다.");
        }

        if (item.getPrice() != null && item.getQuantity() != null) {
            int resultPrice = item.getPrice() * item.getQuantity();
            if (resultPrice < 10000) {
                errors.put("globalError", String.format("가격 * 수량의 합은 10,000원 이상이어야 합니다. 현재값 = %d", resultPrice));
            }
        }

        // 검증에 실패하면 다시 입력 폼으로 보내기
        if (!errors.isEmpty()) {
            log.info("errors = {}", errors);
            model.addAttribute("errors", errors);
            return "validation/v1/addForm";
        }

        // 성공 로직

        Item savedItem = itemRepository.save(item);
        redirectAttributes.addAttribute("itemId", savedItem.getId());
        redirectAttributes.addAttribute("status", true);
        return "redirect:/validation/v1/items/{itemId}";
    }

```
- 컨트롤러에서 검증 로직을 분기문으로 작성하여, 문제가 있을 때마다 Map에 문자열로 오류를 저장함
- 오류가 하나라도 있으면 model에 오류들을 담아서 다시 form을 반환함.

### 오류 발생시 메시지 발생
```html
<form action="item.html" th:action th:object="${item}" method="post">
    <div th:if="${errors?.containsKey('globalError')}">
        <p class="field-error" th:text="${errors['globalError']}">전체 오류 메시지</p>
    </div>
    <div>
        <label for="itemName" th:text="#{label.item.itemName}">상품명</label>
        <input type="text" id="itemName" th:field="*{itemName}"
               th:classappend="${errors?.containsKey('itemName')} ? 'field-error' : _"
               class="form-control" placeholder="이름을 입력하세요">
        <div class="field-error" th:if="${errors?.containsKey('itemName')}" th:text="${errors['itemName']}">
            상품명 오류
        </div>
```
-`${errors?.containsKey('globalError')}`
- `errors?` : errors가 null일 경우 호출시 NullPointerException일 발생함. 이럴 경우 이를 호출한 메서드가 null을 반환하도록 함
- 타임리프에서는 `th:if` 속성에서 null을 false로 처리한다.
- 예외 발생시 `th:classappend`를 통하여, 예외 스타일을 적용함


### V1 방식의 한계
- 뷰 템플릿에서 중복처리할 것이 너무 많음
- 타입에 안 맞는 값은 애초에 컨트롤러에 값이 넘어오기 전에 예외가 발생해버림.
  - 컨트롤러에 넘어올 수 없는 예외에 대해서는 애초에 검증 로직을 수행할 수 없게 되버린다.
- 오류가 발생하면, 다시 화면에 입력값을 넘겨야하는데 타입오류가 발생한 경우에는 애초에 값 저장이 불가능하므로 문자를 보관할 수 없음.
- 결국 고객이 입력한 값을 타입에 무관하게 어딘가에 별도로 관리해야함.

</div>
</details>

---

## V2

- 스프링에서는 검증 오류를 보관하는 클래스로 `BindingResult`를 제공한다.
- BindingResult 덕에 타입에 맞지 않는 값이 들어왔을 경우에도 컨트롤러로 이동할 수 있고, 오류 발생 시 사용자 입력값을 보관하는 것이 편리해진다.
<details>
<summary>상세 설명</summary>
<div markdown="1">

### BindingResult 도입
```java
@PostMapping("/add")
    public String addItemV2(@ModelAttribute Item item, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
```
- BindingResult를 매개변수에 선언해준다. (**`@ModelAttribute`의 대상이 되는 매개변수의 바로 뒤에 선언해야한다.**)
  - BindingResult를 선언해주지 않으면 바인딩 실패시 404오류가 발생하면서 컨트롤러가 호출되지 않고, 오류페이지로 이동함.
  - BindingResult를 선언하면 오류정보를 BindingResult에 담아서 컨트롤러를 정상 호출함
- BindingResult에 검증 오류를 적용하는 방법
  - 개발자가 수동 등록(addError)
  - `@ModelAttribute`의 바인딩 오류 시 스프링에 넣어줌
  - validator 사용
```java
// 검증에 실패하면 다시 입력 폼으로 보내기
if (bindingResult.hasErrors()) {
    log.info("bindingResult = {}", bindingResult);
    return "validation/v2/addForm";
}
```
- BindingResult는 자동으로 Model에 넘어간다.
  - hasErrors() : 등록된 오류가 있으면 true
  - 예외가 있으면 다시 폼을 응답하는 식으로 처리 

### BindingResult의 상속관계
- Errors : BindingResult의 상위 인터페이스
- BindingResult : 인터페이스
  - 추가 기능 추가. 주로 BindingResult를 사용

### 글로벌 오류 - ObjectError
```java
public ObjectError(String objectName, String defaultMessage) {...}

public ObjectError(String objectName, 
@Nullable String[] codes, @Nullable Object[] arguments, 
@Nullable String defaultMessage) {...}
```
- objectName : `@ModelAttribute`로 지정한 이름
- codes : 메시지 코드
  - String[]으로 여러가지 메시지 코드를 저장해둔다. 1순위, 2순위, 3순위, ...를 찾아서 1순위에 있는 메시지로 넘김
- arguments : 메시지에서 사용하는 인자
  - Object[]으로 여러가지 메시지에 사용하는 인자를 지정함
- defaultMessage : 오류 메시지

### 필드 오류 - FieldError

#### 1. FieldError?
- FieldError는 두가지 생성자를 가지고 있다.
- 필드에서 바인딩 오류가 발생할 경우, 스프링은 자동으로 FieldError를 생성하여 BindingResult에 넣어준다.

#### 2. FieldError의 생성자
```java
public FieldError(String objectName, String field, String defaultMessage) {...}

public FieldError(String objectName, String field, 
@Nullable Object rejectedValue, boolean bindingFailure,
@Nullable String[] codes, @Nullable Object[] arguments, 
@Nullable String defaultMessage) {...}
```
- objectName : `@ModelAttribute`로 지정한 이름
- field : 오류가 발생한 필드명
- rejectedValue : 사용자가 입력한 값(거절된 값)
- bindingFilure : 바인딩 실패이면 true, 바인딩 실패가 아닌 경우(검증에서 걸린 경우) false
- codes : 메시지 코드
  - String[]으로 여러가지 메시지 코드를 저장해둔다. 1순위, 2순위, 3순위, ...를 찾아서 1순위에 있는 메시지로 넘김
- arguments : 메시지에서 사용하는 인자
  - Object[]으로 여러가지 메시지에 사용하는 인자를 지정함
- defaultMessage : 오류 메시지

### addForm.html - 글로벌 오류 출력
```html
<form action="item.html" th:action th:object="${item}" method="post">
    <div th:if="${#fields.hasGlobalErrors()}">
        <p class="field-error"
           th:each="err : ${#fields.globalErrors()}" th:text="${err}">글로벌 오류 메시지</p>
    </div>
```
- `#fields` : BindingResult가 제공하는 검증 오류에 접근
  - `"${fields.hasGlobalErrors()}"` : 글로벌 오류가 있는 지 여부 반환
  - `${fields.globalErrors()` : 글로벌 오류들

### addForm.html - 필드 오류 출력
```html
<div>
    <label for="itemName" th:text="#{label.item.itemName}">상품명</label>
    <input type="text" id="itemName" th:field="*{itemName}" 
           th:errorclass="field-error" 
           class="form-control" placeholder="이름을 입력하세요">
    <div class="field-error" 
         th:errors="*{itemName}">
        상품명 오류
    </div>
</div>
```
- `th:errors` : 필드에서 예외가 발생하면 태그를 출력함
- `th:errorclass` : `th:field`에서 지정한 필드에 오류가 있으면 오류 class 속성을 추가함

### 폼 입력 오류 발생 시 값을 유지하는 로직
- 바인딩이 실패하면 스프링은 FieldError를 생성하여 rejectedValue에 사용자 입력값을 저장함. 
- 바인딩 실패가 아닌 경우, 즉 검증 오류일 경우 FieldError의 rejectedValue에 입력값을 저장하여 처리하면 됨.
- 필드에서 오류 발생 시, thymeleaf는 th:field의 값을 바인딩 객체 기준이 아닌, FieldError에서 보관한 값을 출력한다.

### bindingResult - reject(), rejectValue()

- BindingResult는 이미 바인딩 객체를 알고 있다.
- 이런 관점에서 reject, rejectValue는 위에서 했던 Error 생성의 편의성을 제공해준다.
  - BindingResult는 이미 바인딩 객체가 뭔지 알고 있으니 그런 것까지 굳이 알려줄 필요가 없다.
  - 간단히 입력한 오류코드, 바인딩 객체 정보를 기반으로 messageCodeResolver를 통해 메시지 코드를 찾아낼 수 있다.

#### properties의 코드 지정
```properties
range.item.price=가격은 {0} ~ {1}까지 허용합니다.
```
- 맨 앞 : 요구사항, 제약조건
- 가운데 : 객체명
- 맨 뒤 : 필드명
#### reject(...) - ObjectError 지원
```java
// reject 사용례
if (resultPrice < 10000) {
        bindingResult.reject("totalPriceMin", new Object[]{10_000, resultPrice},null);
        }

// rejectValue(...) 사용례
bindingResult.rejectValue("itemName", "required");
```
- rejectValue(...) : FieldError 편의성 제공
- reject(...) : ObjectError 편의성 제공

#### 어떤 원리로 작동하는가?
- bindingResult는 내부적으로 messageCodeResolver를 통해 messageCodes를 만들어낸다.
  - reject (글로벌 에러)
    1. code.객체명
    2. code
  - rejectValue (필드 에러)
    1. code.객체명.필드명
    2. code.필드명
    3. code.필드타입
    4. code
- messageCodes 및 메서드 호출 시 전달한 인자들을 기반으로, 각각 ObjectError, FieldError 메서드를 호출하여 에러를 생성
  - 생성시 messageCodes를 읽고 1번부터 순서대로 errors.properties에서 찾아서 메시지를 만들어냄.
- 에러를 bindingResult에 저장

### 타입 오류 처리

```properties
## 추가 (타입 오류)
typeMismatch.java.lang.Integer=숫자를 입력해주세요.
typeMismatch=타입 오류입니다.
```
- 스프링은 타입 오류가 발생하면 `typeMismatch`오류 코드를 사용함
- 이 오류코드가 MessageCodesResolver를 통하면서 4가지 메시지 코드가 생성됨
- 이 부분에 대해서 errors.properties에서 별도로 메시지를 설정하면 이제, 타입 오류에 대해서도 메시지 처리가 가능해진다.

### validator 분리
```java
public interface Validator {
	boolean supports(Class<?> clazz);
	void validate(Object target, Errors errors);
}
```
- 스프링은 Validator 인터페이스를 제공함
- support : Validator가 해당 클래스를 지원하는가
- validate : 실제 검증로직
  - target : 객체. 형변환해서 사용하면 됨
  - errors : 오류들. BindingResults는 Errors의 하위 인터페이스이므로 이걸 인자로 호출하면 됨
- 검증에 대한 로직을 Validator로 넘김

### WebDataBinder(검증기) 도입
- `@InitBinder` : 검증기를 컨트롤러 호출 시마다 WebDataBinder 인스턴스를 새로 생성후 validator를 호출함.
- `@Validated` : 검증기를 통해 검증할 클래스
- 스프링은 WebMvcBinder에 등록된 검증기를 찾아서 실행함. 여러 검증기를 등록한다면 supports에 의해서 어떤 검증기를 실행할지 구분함.
- 모든 컨트롤러에 대해 글로벌 설정을 하고 싶으면, 메인 클래스에서 WebMvcConfigurer를 구현하고 getValidator() 메서드에서 글로벌 설정할 검증기를 반환하면 됨. (잘 안 씀)

</div>
</details>

---

## Bean Validation
```groovy
implementation 'org.springframework.boot:spring-boot-starter-validation'
```
어노테이션 기반으로 검증로직을 편리하게 적용하는 방법
- 인터페이스 : `jakarta.validation-api`
- 구현체 : `hibernate-validaator`
  - 공식 사이트 : https://hibernate.org/validator/
  - 공식 메뉴얼 : https://docs.jboss.org/hibernate/validator/6.2/reference/en-US/html_single/#preface
  - 검증 어노테이션 모음 : https://docs.jboss.org/hibernate/validator/6.2/reference/en-US/html_single/#validator-defineconstraints-spec
- 스프링 프로젝트가 아닐 경우 사용 방법
  ```java
  ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
  Validator validator = factory.getValidator(); // 빈 검증기
  
  Set<ConstraintViolation<Item>> violations = validator.validate(item);
  ```
- 스프링은 개발자를 위해 이미 빈 검증기를 스프링에 포함하였음.

---

## V3 - Bean Validation 적용

### Bean Validation - 스프링 적용
- 스프링 부트는 `spring-boot-starter-validation`을 의존 라이브러리로 추가하면, 자동으로 BeanValidator을 인지하고 스프링에 통합
- 스프링 부트는 자동으로 `LocalValidatorFactoryBean`을 글로벌 Validator로 등록. 이것은 `@NotNull` 등의 검증 어노테이션을 보고 검증을 수행함.
  - 별도로 글로벌 Validator를 등록해두면 어노테이션 기반의 빈 검증기가 동작하지 않으므로 제거하는 것이 좋다.
- 컨트롤러 단에서, `@Valid` 또는 `@Validated`를 바인딩 객체 앞에 달아주면 FieldError, ObjectError 를 생성하여 BindingResult에 담아줌
  - `@Valid`는 자바 표준, `@Validated`는 스프링 전용. 내부적으로 기능은 스프링 것이 더 많이 포함됨.

### Bean Validation 검증 순서
1. 요청을 읽고 `@ModelAttribute` 각각의 필드에 타입 변환 시도
   - 성공하면 2단계로 진행
   - 실패하면 `typeMismatch`로 `FieldError` 추가
2. Validator 적용
  - 앞에서 필드 에러가 발생했을 경우 이 단계에서 해당필드는 Validator를 적용하지 않는다.

### Bean Validation - 에러코드

```properties
# @NotBlank : 공백 허용하지 않음
NotBlank.객체명.필드명
NotBlank.필드명
NotBlank.타입
NotBlank

## @Range : 범위
Range.객체명.필드명
Range.필드명
Range.타입
Range
```

1. MessageCodesResolver는 실제로 어노테이션명에 해당하는 오류코드를 기반으로 메시지를 찾아줌
2. 실제로 메시지 소스에서 메시지를 지정해주면 됨.
   - 작성 시 인자로 {0}, {1}, {2} 등을 지정 가능
   - 예)
     ```properties
     NotBlank.item.itemName=상품이름을 적어주세요.
     
     NotBlank={0} 공백 허용 안 함.
     Range={0} 오류. {2} ~ {1}의 범위만 허용합니다.
     Max={0} 오류. 최대 {1}까지 허용합니다.
     ```
     - {0} : 필드명
     - {1},{2} : 각 어노테이션마다 다름

#### 메시지 찾는 순서
1. 레벨 순으로 messageSource에서 찾기
2. 어노테이션에 지정한 `@코드명(message="...")` 속성을 읽고 메시지 작성
   ```java
   @NotBlank(message="공백은 입력 못 해욧!")
   private String itemName
   ```
3. 라이브러리가 제공하는 기본 값 사용


### Bean Validation - 글로벌 오류
```java
@ScriptAssert(
        lang = "javascript",
        script = "_this.price * _this.quantity >= 10000",
        message = "총합이 10000원이 넘도록 입력해주세요."
)
public class Item {
```
- 클래스 앞에, `@ScriptAssert()` 어노테이션을 통해 글로벌 오류를 처리할 수 있음.
- 하지만... 제약이 많고 복잡한 경우가 많아서(여러 객체를 참조해야한다거나...) 그렇게 추천되지 않음.
  - 자바코드로 예외를 직접 추가하는 것이 추천됨

### BeanValidation - 한계
```java
    //@NotNull
    private Long id;
```
- 상품 등록 시, 상품 수정 시 각각에 대해 제약조건이 달라질 경우 Item의 어노테이션을 수정할 경우 SideEffect 발생
  - 어느 한쪽 제약사항에 맞춰 제약을 수정하면 다른 쪽에 영향이 발생함.

### BeanValidation - Validated 어노테이션의 groups 기능 적용
```java
    @NotNull(groups = UpdateCheck.class)  // 수정 요구사항 : update 시에는 id 값 존재여부를 확인해야한다.
    private Long id;

    @NotBlank(groups = {SaveCheck.class, UpdateCheck.class})
    private String itemName;

    @NotNull(groups = {SaveCheck.class, UpdateCheck.class})
    @Range(min = 1_000, max = 1_000_000, groups = {SaveCheck.class, UpdateCheck.class})
    private Integer price;

    @NotNull(groups = {SaveCheck.class, UpdateCheck.class})
    @Max(value = 9999, groups = {SaveCheck.class}) // 수정 시에는 quantity 값에 제약조건을 주지 않는다.
    private Integer quantity;
```
- 별도로 그룹핑에 사용할 인터페이스를 생성(SaveCheck.class, UpdateCheck.class)
- 제약조건마다 groups 옵션을 통해 그룹별 제약조건을 지정함
```java
 public String addItemV2(
            @Validated(SaveCheck.class) @ModelAttribute Item item,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
```
- 사용처에서는 제각각 Validated의 옵션을 지정하여 그룹마다 다른 제약조건을 걸 수 있음.
- 하지만... 실제 실무는 같은 도메인에 대한 요청 각각마다 폼을 다르게 분리해서 사용함. (도메인 및 컨트롤러단의 복잡도 증가 등의 여러 이유로...)

## V4 - Form 전송객체 분리

```java
@Data
public class ItemSaveForm {

    @NotBlank
    private String itemName;

    @NotNull
    @Range(min = 1_000, max = 1_000_000)
    private Integer price;

    @NotNull
    @Max(value = 9999)
    private Integer quantity;

}
```
- itemSaveForm, ItemUpdateForm 분리
- 컨트롤러에서는 폼 데이터를 전송받고 이를 기반으로 실제 도메인 객체를 생성함
- 전송하는 폼데이터 맞춤형 객체를 만들어 전달받을 수 있고, 검증도 각각 필요에 맞게 처리할 수 있음.

```java
        // 성공 로직
        Item item = new Item();
        item.setItemName(form.getItemName());
        item.setPrice(form.getPrice());
        item.setQuantity(form.getQuantity());
```
- 다만 컨트롤러단에서 별도로 도메인 객체를 생성하는 로직을 만들어야함.
- 실제로 실무에서는 도메인 객체에 대한 Setter를 사용하지 않도록 해야함. 생성자 하나로 퉁치거나 빌더 패턴 등을 사용하여 객체를 생성하도록 할 것.

---

## API Validation

- HTTP-API를 통해 넘어온 JSON 데이터를 Validation할 때는 쿼리파라미터, 폼데이터 전송과 달리 `@RequestBody`를 통해 JSON 데이터를 객체에 바인딩함.
- `@ModelAttribute`는 파라미터 각각마다 바인딩을 시도하고, 예외가 발생해도 컨트롤러에 값이 넘어올 수 있었음
- `@RequestBody`를 통한 맵핑은 어느 한 필드라도 바인딩에 실패하면 `HttpMessageConverter`단에서 예외가 발생해서 컨트롤러 자체가 호출되지 못 한다.

### API를 통해 넘어온 경우 다음 3가지 경우에 대해 생각해야함
- 성공
- 실패 1 : JSON을 객체로 생성하는 것 자체가 실패함.
  - 이 부분은 뒷 장에서 배움
- 실패 2 : JSON을 객체로 생성하는 것 자체는 성공했으나 검증에서 실패함.

---
