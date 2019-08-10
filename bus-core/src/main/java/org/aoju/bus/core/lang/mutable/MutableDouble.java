/*
 * The MIT License
 *
 * Copyright (c) 2017, aoju.org All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.aoju.bus.core.lang.mutable;


import org.aoju.bus.core.utils.NumberUtils;

/**
 * 可变 <code>double</code> 类型
 *
 * @author Kimi Liu
 * @version 3.0.0
 * @see Double
 * @since JDK 1.8
 */
public class MutableDouble extends Number implements Comparable<MutableDouble>, Mutable<Number> {

    private static final long serialVersionUID = -7381592836008495052L;

    private double value;

    /**
     * 构造，默认值0
     */
    public MutableDouble() {
        super();
    }

    /**
     * 构造
     *
     * @param value 值
     */
    public MutableDouble(final double value) {
        super();
        this.value = value;
    }

    /**
     * 构造
     *
     * @param value 值
     */
    public MutableDouble(final Number value) {
        this(value.doubleValue());
    }

    /**
     * 构造
     *
     * @param value String值
     * @throws NumberFormatException 数字转换错误
     */
    public MutableDouble(final String value) throws NumberFormatException {
        super();
        this.value = Double.parseDouble(value);
    }

    @Override
    public Double get() {
        return Double.valueOf(this.value);
    }

    /**
     * 设置值
     *
     * @param value 值
     */
    public void set(final double value) {
        this.value = value;
    }

    @Override
    public void set(final Number value) {
        this.value = value.doubleValue();
    }

    /**
     * 值+1
     *
     * @return this
     */
    public MutableDouble increment() {
        value++;
        return this;
    }

    /**
     * 值减一
     *
     * @return this
     */
    public MutableDouble decrement() {
        value--;
        return this;
    }

    /**
     * 增加值
     *
     * @param operand 被增加的值
     * @return this
     */
    public MutableDouble add(final double operand) {
        this.value += operand;
        return this;
    }

    /**
     * 增加值
     *
     * @param operand 被增加的值，非空
     * @return this
     */
    public MutableDouble add(final Number operand) {
        this.value += operand.doubleValue();
        return this;
    }

    /**
     * 减去值
     *
     * @param operand 被减的值
     * @return this
     */
    public MutableDouble subtract(final double operand) {
        this.value -= operand;
        return this;
    }

    /**
     * 减去值
     *
     * @param operand 被减的值，非空
     * @return this
     */
    public MutableDouble subtract(final Number operand) {
        this.value -= operand.doubleValue();
        return this;
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return (long) value;
    }

    @Override
    public float floatValue() {
        return (float) value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    /**
     * 相等需同时满足如下条件：
     * <ol>
     * <li>非空</li>
     * <li>类型为 {@link MutableDouble}</li>
     * <li>值相等</li>
     * </ol>
     *
     * @param obj 比对的对象
     * @return 相同返回<code>true</code>，否则 <code>false</code>
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof MutableDouble) {
            return (Double.doubleToLongBits(((MutableDouble) obj).value) == Double.doubleToLongBits(value));
        }
        return false;
    }

    @Override
    public int hashCode() {
        final long bits = Double.doubleToLongBits(value);
        return (int) (bits ^ bits >>> 32);
    }

    /**
     * 比较
     *
     * @param other 其它 {@link MutableDouble} 对象
     * @return x==y返回0，x&lt;y返回-1，x&gt;y返回1
     */
    @Override
    public int compareTo(final MutableDouble other) {
        return NumberUtils.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}