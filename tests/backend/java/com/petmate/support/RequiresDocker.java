package com.petmate.support;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「本用例需要 Docker」。Docker 不可用时用例被跳过而非失败。
 *
 * @see DockerAvailable
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerAvailable.class)
public @interface RequiresDocker {
}
