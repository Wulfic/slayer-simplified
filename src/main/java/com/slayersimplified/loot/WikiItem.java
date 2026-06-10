/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Ported from loot-lookup-plugin by donth77.
 */
package com.slayersimplified.loot;

import java.text.NumberFormat;

/**
 * Represents a single item drop from the OSRS Wiki drop table.
 * Ported from loot-lookup-plugin.
 */
public class WikiItem
{
    private final String imageUrl;
    private final String name;
    private final int quantity;
    private final String quantityStr;
    private final String rarityStr;
    private final double rarity;
    private final int exchangePrice;
    private final int alchemyPrice;

    private final NumberFormat nf = NumberFormat.getNumberInstance();

    public WikiItem(String imageUrl, String name, int quantity, String quantityStr,
                    String rarityStr, double rarity, int exchangePrice, int alchemyPrice)
    {
        this.imageUrl = imageUrl;
        this.name = name;
        this.quantity = quantity;
        this.quantityStr = quantityStr;
        this.rarityStr = rarityStr;
        this.rarity = rarity;
        this.exchangePrice = exchangePrice;
        this.alchemyPrice = alchemyPrice;
    }

    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public String getQuantityStr() { return quantityStr; }
    public double getRarity() { return rarity; }
    public String getRarityStr() { return rarityStr; }
    public int getExchangePrice() { return exchangePrice; }
    public int getAlchemyPrice() { return alchemyPrice; }
    public String getImageUrl() { return imageUrl; }

    public WikiItem withPrices(int exchangePrice, int alchemyPrice)
    {
        return new WikiItem(imageUrl, name, quantity, quantityStr, rarityStr, rarity, exchangePrice, alchemyPrice);
    }

    public String getQuantityLabelText()
    {
        if (quantityStr.contains("-") || quantityStr.endsWith(" (noted)"))
        {
            return "x" + quantityStr;
        }
        return quantity > 0 ? "x" + nf.format(quantity) : quantityStr;
    }

    public String getPriceLabelText()
    {
        if (exchangePrice > 0)
        {
            return nf.format(exchangePrice) + " gp";
        }
        return name.equals("Nothing") ? "" : "Not sold";
    }

    public String getRarityLabelText()
    {
        if (rarity == 1.0)
        {
            return "Always";
        }
        if (rarity > 0)
        {
            return rarityStr;
        }
        return rarityStr;
    }
}
