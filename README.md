
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

---

</div>
</details>

---
