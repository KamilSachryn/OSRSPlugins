package com.example.kamiWinter;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
enum KamiActivity
{
    IDLE("IDLE"),
    WOODCUTTING("Woodcutting"),
    FLETCHING("Fletching"),
    FEEDING_BRAZIER("Feeding"),
    FIXING_BRAZIER("Fixing"),
    LIGHTING_BRAZIER("Lighting");

    private final String actionString;
}
