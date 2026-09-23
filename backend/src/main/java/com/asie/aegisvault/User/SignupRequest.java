package com.asie.aegisvault.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 회원가입 화면의 입력값입니다. 비밀번호 확인은 DB에 저장하지 않습니다.
@Getter
@Setter
public class SignupRequest {
    @NotBlank(message = "아이디를 입력해주세요")
    @Size(max = 100, message = "아이디는 100자 이하로 입력해주세요")
    private String nickname;

    @NotBlank(message = "이메일을 입력해주세요")
    @Email(message = "올바른 이메일 주소를 입력해주세요")
    @Size(max = 255, message = "이메일은 255자 이하로 입력해주세요")
    private String email;

    @NotBlank(message = "비밀번호를 입력해주세요")
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력해주세요")
    private String passwordConfirm;
}
