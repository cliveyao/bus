/*
 * The MIT License
 *
 * Copyright (c) 2017 aoju.org All rights reserved.
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
package org.aoju.bus.health.hardware;

/**
 * The GlobalMemory class tracks information about the use of a computer's
 * physical memory (RAM) as well as any available virtual memory.
 *
 * @author Kimi Liu
 * @version 5.3.5
 * @since JDK 1.8+
 */
public interface GlobalMemory {

    /**
     * The amount of actual physical memory, in bytes.
     *
     * @return Total number of bytes.
     */
    long getTotal();

    /**
     * The amount of physical memory currently available, in bytes.
     *
     * @return Available number of bytes.
     */
    long getAvailable();

    /**
     * The number of bytes in a memory page
     *
     * @return Page size in bytes.
     */
    long getPageSize();

    /**
     * Virtual memory, such as a swap file.
     *
     * @return A VirtualMemory object.
     */
    VirtualMemory getVirtualMemory();

    /**
     * Physical memory, such as banks of memory.
     * <p>
     * On Linux, requires elevated permissions. On FreeBSD and Solaris, requires
     * installation of dmidecode.
     *
     * @return A list of PhysicalMemory objects.
     */
    PhysicalMemory[] getPhysicalMemory();

}
