package com.poe2runechecker.model;

/**
 * Одна строка окна "Runeshape Combinations":
 * слева иконки рун (их игнорируем), справа результат — например "2x Gemcutter's Prism".
 */
public class RecipeRow {
    public final int quantity;       // 2
    public final String itemName;    // "Gemcutter's Prism"
    public final int screenY;        // y-координата строки на экране (для отрисовки оверлея)
    public int screenXEnd;           // x правого края текста строки (куда писать цену)

    public double unitPrice = 0;     // цена за 1 шт в chaos/exalt (с poe.ninja)
    public double totalValue = 0;    // quantity * unitPrice
    public boolean priceUnknown = false; // true = цену определить нельзя (рисуем N/A)
    public boolean explicitQty = false;  // true = "Nx" реально распозналось (не дефолт 1)

    public RecipeRow(int quantity, String itemName, int screenY) {
        this.quantity = quantity;
        this.itemName = itemName;
        this.screenY = screenY;
    }

    @Override
    public String toString() {
        return quantity + "x " + itemName + "  =  " + String.format("%.1f", totalValue);
    }
}
