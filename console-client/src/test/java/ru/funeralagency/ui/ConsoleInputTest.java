package ru.funeralagency.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsoleInputTest {

    @Test
    void asksAgainWhenIdIsNotANumber() {
        ConsoleInput input = inputWith("текст\n-1\n5\n");

        assertEquals(5, input.readPositiveLong("ID: "));
    }

    @Test
    void asksAgainWhenPriceIsInvalid() {
        ConsoleInput input = inputWith("-10\n100.123\n1500.50\n");

        assertEquals(new BigDecimal("1500.50"), input.readPrice(""));
    }

    @Test
    void keepsCurrentValueWhenUpdateInputIsEmpty() {
        ConsoleInput input = inputWith("\n");

        assertEquals(
                "Текущее значение",
                input.readRequiredText("Поле: ", "Текущее значение", 255)
        );
    }

    private ConsoleInput inputWith(String values) {
        return new ConsoleInput(new Scanner(values));
    }
}
