
# java-spring-validation

우아한형제들 김영한 님의 인프런 강의 '스프링 MVC 2편 - 백엔드 웹 개발 활용 기술'의 4,5장, "검증" 부분을 따라치면서 정리하는 Repository

---

## V1

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

---

## V2

- 스프링에서는 검증 오류를 보관하는 클래스로 `BindingResult`를 제공한다.

### BindingResult 도입
```java
   @PostMapping("/add")
    public String addItemV1(@ModelAttribute Item item, BindingResult bindingResult, RedirectAttributes redirectAttributes, Model model) {

        // 검증 오류 결과를 보관 : BindingResult에서 담당하도록 함

        // 검증 로직
        if (!StringUtils.hasText(item.getItemName())) {
            bindingResult.addError(new FieldError("item", "itemName", "상품 이름은 필수 입니다."));
        }

        if (item.getPrice() == null || item.getPrice() < 1000 || item.getPrice() > 1_000_000) {
            bindingResult.addError(new FieldError("item", "price", "가격은 1,000 ~ 1,000,000까지 허용됩니다."));
        }

        if (item.getQuantity() == null || item.getQuantity() > 9999) {
            bindingResult.addError(new FieldError("item", "quantity", "수량은 최대 9,999까지 허용됩니다."));
        }

        if (item.getPrice() != null && item.getQuantity() != null) {
            int resultPrice = item.getPrice() * item.getQuantity();
            if (resultPrice < 10000) {
                bindingResult.addError(new ObjectError("item",
                        String.format("가격 * 수량의 합은 10,000원 이상이어야 합니다. 현재값 = %d", resultPrice)));
            }
        }

        // 검증에 실패하면 다시 입력 폼으로 보내기
        if (bindingResult.hasErrors()) {
            log.info("bindingResult = {}", bindingResult);
            return "validation/v2/addForm";
        }

        // 성공 로직
        Item savedItem = itemRepository.save(item);
        redirectAttributes.addAttribute("itemId", savedItem.getId());
        redirectAttributes.addAttribute("status", true);
        return "redirect:/validation/v2/items/{itemId}";
    }
```
- BindingResult를 매개변수에 선언해준다. (**`@ModelAttribute`의 대상이 되는 매개변수의 바로 뒤에 선언해야한다.**)
- Map에 일일이 저장하는 대신 BindingResult에 `addError` 해주면 된다.
  - ObjectError : 글로벌오류
  - FieldError : 특정 필드에서 발생한 오류(ObjectError의 하위 클래스)
- BindingResult는 자동으로 Model에 넘어간다.
  - hasErrors() : 등록된 오류가 있으면 true

### 글로벌 오류 - ObjectError
```java
public ObjectError(String objectName, String defaultMessage) {...}
```
- objectName : `@ModelAttribute`로 지정한 이름
- defaultMessage : 오류 메시지

### 필드 오류 - FieldError
```java
public FieldError(String objectName, String field, String defaultMessage) {...}
```
- objectName : `@ModelAttribute`로 지정한 이름
- field : 오류가 발생한 필드명
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

---
