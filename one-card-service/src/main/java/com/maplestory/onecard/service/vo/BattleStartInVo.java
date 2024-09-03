package com.maplestory.onecard.service.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
@Data
public class BattleStartInVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "房间号不能为空")
    @Pattern(regexp = "^\\d{4}$", message = "房间号只能为4位数字")
    private String roomNumber;
}
