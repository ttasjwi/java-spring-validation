package hello.itemservice.domain.item;

import lombok.Data;


//@ScriptAssert(
//        lang = "javascript",
//        script = "_this.price * _this.quantity >= 10000",
//        message = "총합이 10000원이 넘도록 입력해주세요."
//)
@Data
public class Item {

//    @NotNull(groups = UpdateCheck.class)  // 수정 요구사항 : update 시에는 id 값 존재여부를 확인해야한다.
    private Long id;

//    @NotBlank(groups = {SaveCheck.class, UpdateCheck.class})
    private String itemName;

//    @NotNull(groups = {SaveCheck.class, UpdateCheck.class})
//    @Range(min = 1_000, max = 1_000_000, groups = {SaveCheck.class, UpdateCheck.class})
    private Integer price;

//    @NotNull(groups = {SaveCheck.class, UpdateCheck.class})
//    @Max(value = 9999, groups = {SaveCheck.class}) // 수정 시에는 quantity 값에 제약조건을 주지 않는다.
    private Integer quantity;

    public Item() {
    }

    public Item(String itemName, Integer price, Integer quantity) {
        this.itemName = itemName;
        this.price = price;
        this.quantity = quantity;
    }
}
