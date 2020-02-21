/*
 * The MIT License
 *
 * Copyright (c) 2015-2020 aoju.org All rights reserved.
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
package org.aoju.bus.health.software.linux;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.linux.LibC;
import com.sun.jna.platform.linux.LibC.Sysinfo;
import org.aoju.bus.core.lang.Normal;
import org.aoju.bus.core.lang.Symbol;
import org.aoju.bus.health.Builder;
import org.aoju.bus.health.Command;
import org.aoju.bus.health.common.linux.LinuxLibc;
import org.aoju.bus.health.common.linux.ProcUtils;
import org.aoju.bus.health.software.*;
import org.aoju.bus.logger.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.*;

/**
 * <p>
 * LinuxOperatingSystem class.
 * </p>
 *
 * @author Kimi Liu
 * @version 5.6.3
 * @since JDK 1.8+
 */
public class LinuxOS extends AbstractOS {

    private static final long BOOTTIME;
    // Get a list of orders to pass to ParseUtil
    private static final int[] PROC_PID_STAT_ORDERS = new int[ProcPidStat.values().length];
    // 2.6 Kernel has 44 elements, 3.3 has 47, and 3.5 has 52.
    // Check /proc/self/stat to find its length
    private static final int PROC_PID_STAT_LENGTH;
    // Jiffies per second, used for process time counters.
    private static final long USER_HZ = Builder.parseLongOrDefault(Command.getFirstAnswer("getconf CLK_TCK"),
            100L);
    // Boot time in MS.
    private static final long BOOT_TIME;

    static {
        // Boot time given by btime variable in /proc/stat.
        List<String> procStat = Builder.readFile("/proc/stat");
        long tempBT = 0;
        for (String stat : procStat) {
            if (stat.startsWith("btime")) {
                String[] bTime = Builder.whitespaces.split(stat);
                tempBT = Builder.parseLongOrDefault(bTime[1], 0L);
                break;
            }
        }
        // If above fails, current time minus uptime.
        if (tempBT == 0) {
            tempBT = System.currentTimeMillis() / 1000L - (long) ProcUtils.getSystemUptimeSeconds();
        }
        BOOTTIME = tempBT;
    }

    static {
        for (ProcPidStat stat : ProcPidStat.values()) {
            // The PROC_PID_STAT enum indices are 1-indexed.
            // Subtract one to get a zero-based index
            PROC_PID_STAT_ORDERS[stat.ordinal()] = stat.getOrder() - 1;
        }
    }

    static {
        String stat = Builder.getStringFromFile(ProcUtils.getProcPath() + "/self/stat");
        if (!stat.isEmpty() && stat.contains(Symbol.PARENTHESE_RIGHT)) {
            // add 3 to account for pid, process name in prarenthesis, and state
            PROC_PID_STAT_LENGTH = Builder.countStringToLongArray(stat, Symbol.C_SPACE) + 3;
        } else {
            // Default assuming recent kernel
            PROC_PID_STAT_LENGTH = 52;
        }
    }

    static {
        // Uptime is only in hundredths of seconds but we need thousandths.
        // We can grab uptime twice and take average to reduce error, getting
        // current time in between
        double uptime = ProcUtils.getSystemUptimeSeconds();
        long now = System.currentTimeMillis();
        uptime += ProcUtils.getSystemUptimeSeconds();
        // Uptime is now 2x seconds, so divide by 2, but
        // we want milliseconds so multiply by 1000
        // Ultimately multiply by 1000/2 = 500
        BOOT_TIME = now - (long) (500d * uptime + 0.5);
        // Cast/truncation is effectively rounding. Boot time could
        // be +/- 5 ms due to System Uptime rounding to nearest 10ms.
    }

    // Resident Set Size is given as number of pages the process has in real
    // memory.
    // To get the actual size in bytes we need to multiply that with page size.
    private final int memoryPageSize;
    // Populated with results of reading /etc/os-release or other files
    private String versionId;
    private String codeName;

    /**
     * <p>
     * Constructor for LinuxOperatingSystem.
     * </p>
     */
    public LinuxOS() {
        super.getVersionInfo();
        // The above call may also populate versionId and codeName
        // to pass to version constructor
        this.memoryPageSize = Builder.parseIntOrDefault(Command.getFirstAnswer("getconf PAGESIZE"), 4096);
    }

    private static int getParentPidFromProcFile(int pid) {
        String stat = Builder.getStringFromFile(String.format("/proc/%d/stat", pid));
        long[] statArray = Builder.parseStringToLongArray(stat, PROC_PID_STAT_ORDERS, PROC_PID_STAT_LENGTH, Symbol.C_SPACE);
        return (int) statArray[ProcPidStat.PPID.ordinal()];
    }

    /**
     * Looks for a collection of possible distrib-release filenames
     *
     * @return The first valid matching filename
     */
    protected static String getReleaseFilename() {
        // Look for any /etc/*-release, *-version, and variants
        File etc = new File("/etc");
        // Find any *_input files in that path
        File[] matchingFiles = etc.listFiles(//
                f -> (f.getName().endsWith("-release") || //
                        f.getName().endsWith("-version") || //
                        f.getName().endsWith("_release") || //
                        f.getName().endsWith("_version")) //
                        && !(f.getName().endsWith("os-release") || //
                        f.getName().endsWith("lsb-release") || //
                        f.getName().endsWith("system-release")));
        if (matchingFiles != null && matchingFiles.length > 0) {
            return matchingFiles[0].getPath();
        }
        if (new File("/etc/release").exists()) {
            return "/etc/release";
        }
        // If all else fails, try this
        return "/etc/issue";
    }

    /**
     * Converts a portion of a filename (e.g. the 'redhat' in /etc/redhat-release)
     * to a mixed case string representing the family (e.g., Red Hat)
     *
     * @param name Stripped version of filename after removing /etc and -release
     * @return Mixed case family
     */
    private static String filenameToFamily(String name) {
        switch (name.toLowerCase()) {
            // Handle known special cases
            case Normal.EMPTY:
                return "Solaris";
            case "blackcat":
                return "Black Cat";
            case "bluewhite64":
                return "BlueWhite64";
            case "e-smith":
                return "SME Server";
            case "eos":
                return "FreeEOS";
            case "hlfs":
                return "HLFS";
            case "lfs":
                return "Linux-From-Scratch";
            case "linuxppc":
                return "Linux-PPC";
            case "meego":
                return "MeeGo";
            case "mandakelinux":
                return "Mandrake";
            case "mklinux":
                return "MkLinux";
            case "nld":
                return "Novell Linux Desktop";
            case "novell":
            case "SuSE":
                return "SUSE Linux";
            case "pld":
                return "PLD";
            case "redhat":
                return "Red Hat Linux";
            case "sles":
                return "SUSE Linux ES9";
            case "sun":
                return "Sun JDS";
            case "synoinfo":
                return "Synology";
            case "tinysofa":
                return "Tiny Sofa";
            case "turbolinux":
                return "TurboLinux";
            case "ultrapenguin":
                return "UltraPenguin";
            case "va":
                return "VA-Linux";
            case "vmware":
                return "VMWareESX";
            case "yellowdog":
                return "Yellow Dog";

            // /etc/issue will end up here:
            case "issue":
                return "Unknown";
            // If not a special case just capitalize first letter
            default:
                return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    /**
     * Gets Jiffies per second, useful for converting ticks to milliseconds and vice
     * versa.
     *
     * @return Jiffies per second.
     */
    public static long getHz() {
        return USER_HZ;
    }

    @Override
    public String queryManufacturer() {
        return "GNU/Linux";
    }

    @Override
    public FamilyVersionInfo queryFamilyVersionInfo() {
        String family = queryFamilyFromReleaseFiles();
        OSVersionInfo versionInfo = new OSVersionInfo(this.versionId, this.codeName, null);
        return new FamilyVersionInfo(family, versionInfo);
    }

    @Override
    protected int queryBitness(int jvmBitness) {
        if (jvmBitness < 64 && Command.getFirstAnswer("uname -m").indexOf("64") == -1) {
            return jvmBitness;
        }
        return 64;
    }

    @Override
    protected boolean queryElevated() {
        return System.getenv("SUDO_COMMAND") != null;
    }

    @Override
    public FileSystem getFileSystem() {
        return new LinuxFileSystem();
    }

    @Override
    public OSProcess[] getProcesses(int limit, ProcessSort sort, boolean slowFields) {
        List<OSProcess> procs = new ArrayList<>();
        File[] pids = ProcUtils.getPidFiles();
        LinuxUserGroupInfo userGroupInfo = new LinuxUserGroupInfo();

        // now for each file (with digit name) get process info
        for (File pidFile : pids) {
            int pid = Builder.parseIntOrDefault(pidFile.getName(), 0);
            OSProcess proc = getProcess(pid, userGroupInfo, slowFields);
            if (proc != null) {
                procs.add(proc);
            }
        }
        // Sort
        List<OSProcess> sorted = processSort(procs, limit, sort);
        return sorted.toArray(new OSProcess[0]);
    }

    @Override
    public OSProcess getProcess(int pid, boolean slowFields) {
        return getProcess(pid, new LinuxUserGroupInfo(), slowFields);
    }

    private OSProcess getProcess(int pid, LinuxUserGroupInfo userGroupInfo, boolean slowFields) {
        String path = Normal.EMPTY;
        Pointer buf = new Memory(1024);
        int size = LinuxLibc.INSTANCE.readlink(String.format("/proc/%d/exe", pid), buf, 1023);
        if (size > 0) {
            String tmp = buf.getString(0);
            path = tmp.substring(0, tmp.length() < size ? tmp.length() : size);
        }
        Map<String, String> io = Builder.getKeyValueMapFromFile(String.format("/proc/%d/io", pid), Symbol.COLON);
        // See man proc for how to parse /proc/[pid]/stat
        long now = System.currentTimeMillis();
        String stat = Builder.getStringFromFile(String.format("/proc/%d/stat", pid));
        // A race condition may leave us with an empty string
        if (stat.isEmpty()) {
            return null;
        }
        // We can get name and status more easily from /proc/pid/status which we
        // call later, so just get the numeric bits here
        long[] statArray = Builder.parseStringToLongArray(stat, PROC_PID_STAT_ORDERS, PROC_PID_STAT_LENGTH, Symbol.C_SPACE);
        // Fetch cached process if it exists
        OSProcess proc = new OSProcess(this);
        proc.setProcessID(pid);
        // The /proc/pid/cmdline value is null-delimited
        proc.setCommandLine(Builder.getStringFromFile(String.format("/proc/%d/cmdline", pid)));
        long startTime = BOOT_TIME + statArray[ProcPidStat.START_TIME.ordinal()] * 1000L / USER_HZ;
        // BOOT_TIME could be up to 5ms off. In rare cases when a process has
        // started within 5ms of boot it is possible to get negative uptime.
        if (startTime >= now) {
            startTime = now - 1;
        }
        proc.setStartTime(startTime);
        proc.setParentProcessID((int) statArray[ProcPidStat.PPID.ordinal()]);
        proc.setThreadCount((int) statArray[ProcPidStat.THREAD_COUNT.ordinal()]);
        proc.setPriority((int) statArray[ProcPidStat.PRIORITY.ordinal()]);
        proc.setVirtualSize(statArray[ProcPidStat.VSZ.ordinal()]);
        proc.setResidentSetSize(statArray[ProcPidStat.RSS.ordinal()] * this.memoryPageSize);
        proc.setKernelTime(statArray[ProcPidStat.KERNEL_TIME.ordinal()] * 1000L / USER_HZ);
        proc.setUserTime(statArray[ProcPidStat.USER_TIME.ordinal()] * 1000L / USER_HZ);
        proc.setUpTime(now - proc.getStartTime());
        // See man proc for how to parse /proc/[pid]/io
        proc.setBytesRead(Builder.parseLongOrDefault(io.getOrDefault("read_bytes", Normal.EMPTY), 0L));
        proc.setBytesWritten(Builder.parseLongOrDefault(io.getOrDefault("write_bytes", Normal.EMPTY), 0L));

        // gets the open files count
        if (slowFields) {
            List<String> openFilesList = Command.runNative(String.format("ls -f /proc/%d/fd", pid));
            proc.setOpenFiles(openFilesList.size() - 1L);

            // get 5th byte of file for 64-bit check
            // https://en.wikipedia.org/wiki/Executable_and_Linkable_Format#File_header
            byte[] buffer = new byte[5];
            if (!path.isEmpty()) {
                try (InputStream is = new FileInputStream(path)) {
                    if (is.read(buffer) == buffer.length) {
                        proc.setBitness(buffer[4] == 1 ? 32 : 64);
                    }
                } catch (IOException e) {
                    Logger.warn("Failed to read process file: {}", path);
                }
            }
        }

        Map<String, String> status = Builder.getKeyValueMapFromFile(String.format("/proc/%d/status", pid), Symbol.COLON);
        proc.setName(status.getOrDefault("Name", Normal.EMPTY));
        proc.setPath(path);
        switch (status.getOrDefault("State", "U").charAt(0)) {
            case 'R':
                proc.setState(OSProcess.State.RUNNING);
                break;
            case 'S':
                proc.setState(OSProcess.State.SLEEPING);
                break;
            case 'D':
                proc.setState(OSProcess.State.WAITING);
                break;
            case 'Z':
                proc.setState(OSProcess.State.ZOMBIE);
                break;
            case 'T':
                proc.setState(OSProcess.State.STOPPED);
                break;
            default:
                proc.setState(OSProcess.State.OTHER);
                break;
        }
        proc.setUserID(Builder.whitespaces.split(status.getOrDefault("Uid", Normal.EMPTY))[0]);
        proc.setGroupID(Builder.whitespaces.split(status.getOrDefault("Gid", Normal.EMPTY))[0]);
        OSUser user = userGroupInfo.getUser(proc.getUserID());
        if (user != null) {
            proc.setUser(user.getUserName());
        }
        proc.setGroup(userGroupInfo.getGroupName(proc.getGroupID()));

        try {
            String cwdLink = String.format("/proc/%d/cwd", pid);
            String cwd = new File(cwdLink).getCanonicalPath();
            if (!cwd.equals(cwdLink)) {
                proc.setCurrentWorkingDirectory(cwd);
            }
        } catch (IOException e) {
            Logger.trace("Couldn't find cwd for pid {}: {}", pid, e);
        }
        return proc;
    }

    @Override
    public OSProcess[] getChildProcesses(int parentPid, int limit, ProcessSort sort) {
        List<OSProcess> procs = new ArrayList<>();
        File[] procFiles = ProcUtils.getPidFiles();
        LinuxUserGroupInfo userGroupInfo = new LinuxUserGroupInfo();

        // now for each file (with digit name) get process info
        for (File procFile : procFiles) {
            int pid = Builder.parseIntOrDefault(procFile.getName(), 0);
            if (parentPid == getParentPidFromProcFile(pid)) {
                OSProcess proc = getProcess(pid, userGroupInfo, true);
                if (proc != null) {
                    procs.add(proc);
                }
            }
        }
        List<OSProcess> sorted = processSort(procs, limit, sort);
        return sorted.toArray(new OSProcess[0]);
    }

    @Override
    public long getProcessAffinityMask(int processId) {
        // Would prefer to use native sched_getaffinity call but variable sizing is
        // kernel-dependent and requires C macros, so we use command line instead.
        String mask = Command.getFirstAnswer("taskset -p " + processId);
        // Output:
        // pid 3283's current affinity mask: 3
        // pid 9726's current affinity mask: f
        String[] split = Builder.whitespaces.split(mask);
        try {
            return new BigInteger(split[split.length - 1], 16).longValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public int getProcessId() {
        return LinuxLibc.INSTANCE.getpid();
    }

    @Override
    public int getProcessCount() {
        return ProcUtils.getPidFiles().length;
    }

    @Override
    public int getThreadCount() {
        try {
            Sysinfo info = new Sysinfo();
            if (0 != LibC.INSTANCE.sysinfo(info)) {
                Logger.error("Failed to get process thread count. Error code: {}", Native.getLastError());
                return 0;
            }
            return info.procs;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            Logger.error("Failed to get procs from sysinfo. {}", e);
        }
        return 0;
    }

    @Override
    public long getSystemUptime() {
        return (long) ProcUtils.getSystemUptimeSeconds();
    }

    @Override
    public long getSystemBootTime() {
        return BOOTTIME;
    }

    @Override
    public NetworkParams getNetworkParams() {
        return new LinuxNetwork();
    }

    private String queryFamilyFromReleaseFiles() {
        String family;
        // There are two competing options for family/version information.
        // Newer systems are adopting a standard /etc/os-release file:
        // https://www.freedesktop.org/software/systemd/man/os-release.html
        //
        // Some systems are still using the lsb standard which parses a
        // variety of /etc/*-release files and is most easily accessed via
        // the commandline lsb_release -a, see here:
        // http://linux.die.net/man/1/lsb_release
        // In this case, the /etc/lsb-release file (if it exists) has
        // optional overrides to the information in the /etc/distrib-release
        // files, which show: "Distributor release x.x (Codename)"
        //

        // Attempt to read /etc/system-release which has more details than
        // os-release on (CentOS and Fedora)
        if ((family = readDistribRelease("/etc/system-release")) != null) {
            // If successful, we're done. this.family has been set and
            // possibly the versionID and codeName
            return family;
        }

        // Attempt to read /etc/os-release file.
        if ((family = readOsRelease()) != null) {
            // If successful, we're done. this.family has been set and
            // possibly the versionID and codeName
            return family;
        }

        // Attempt to execute the `lsb_release` command
        if ((family = execLsbRelease()) != null) {
            // If successful, we're done. this.family has been set and
            // possibly the versionID and codeName
            return family;
        }

        // The above two options should hopefully work on most
        // distributions. If not, we keep having fun.
        // Attempt to read /etc/lsb-release file
        if ((family = readLsbRelease()) != null) {
            // If successful, we're done. this.family has been set and
            // possibly the versionID and codeName
            return family;
        }

        // If we're still looking, we search for any /etc/*-release (or
        // similar) filename, for which the first line should be of the
        // "Distributor release x.x (Codename)" format or possibly a
        // "Distributor VERSION x.x (Codename)" format
        String etcDistribRelease = getReleaseFilename();
        if ((family = readDistribRelease(etcDistribRelease)) != null) {
            // If successful, we're done. this.family has been set and
            // possibly the versionID and codeName
            return family;
        }
        // If we've gotten this far with no match, use the distrib-release
        // filename (defaults will eventually give "Unknown")
        return filenameToFamily(etcDistribRelease.replace("/etc/", Normal.EMPTY).replace("release", "").replace("version", Normal.EMPTY)
                .replace(Symbol.HYPHEN, Normal.EMPTY).replace(Symbol.UNDERLINE, Normal.EMPTY));
    }

    /**
     * Attempts to read /etc/os-release
     *
     * @return true if file successfully read and NAME= found
     */
    private String readOsRelease() {
        String family = null;
        if (new File("/etc/os-release").exists()) {
            List<String> osRelease = Builder.readFile("/etc/os-release");
            // Search for NAME=
            for (String line : osRelease) {
                if (line.startsWith("VERSION=")) {
                    Logger.debug("os-release: {}", line);
                    line = line.replace("VERSION=", Normal.EMPTY).replaceAll("^\"|\"$", "").trim();
                    String[] split = line.split("[()]");
                    if (split.length <= 1) {
                        // If no parentheses, check for Ubuntu's comma format
                        split = line.split(", ");
                    }
                    if (split.length > 0) {
                        this.versionId = split[0].trim();
                    }
                    if (split.length > 1) {
                        this.codeName = split[1].trim();
                    }
                } else if (line.startsWith("NAME=") && family == null) {
                    Logger.debug("os-release: {}", line);
                    family = line.replace("NAME=", Normal.EMPTY).replaceAll("^\"|\"$", "").trim();
                } else if (line.startsWith("VERSION_ID=") && this.versionId == null) {
                    Logger.debug("os-release: {}", line);
                    this.versionId = line.replace("VERSION_ID=", Normal.EMPTY).replaceAll("^\"|\"$", "").trim();
                }
            }
        }
        return family;
    }

    /**
     * Attempts to execute `lsb_release -a`
     *
     * @return true if the command successfully executed and Distributor ID: or
     * Description: found
     */
    private String execLsbRelease() {
        String family = null;
        // If description is of the format Distrib release x.x (Codename)
        // that is primary, otherwise use Distributor ID: which returns the
        // distribution concatenated, e.g., RedHat instead of Red Hat
        for (String line : Command.runNative("lsb_release -a")) {
            if (line.startsWith("Description:")) {
                Logger.debug("lsb_release -a: {}", line);
                line = line.replace("Description:", Normal.EMPTY).trim();
                if (line.contains(" release ")) {
                    family = parseRelease(line, " release ");
                }
            } else if (line.startsWith("Distributor ID:") && family == null) {
                Logger.debug("lsb_release -a: {}", line);
                family = line.replace("Distributor ID:", Normal.EMPTY).trim();
            } else if (line.startsWith("Release:") && this.versionId == null) {
                Logger.debug("lsb_release -a: {}", line);
                this.versionId = line.replace("Release:", Normal.EMPTY).trim();
            } else if (line.startsWith("Codename:") && this.codeName == null) {
                Logger.debug("lsb_release -a: {}", line);
                this.codeName = line.replace("Codename:", Normal.EMPTY).trim();
            }
        }
        return family;
    }

    /**
     * Attempts to read /etc/lsb-release
     *
     * @return true if file successfully read and DISTRIB_ID or DISTRIB_DESCRIPTION
     * found
     */
    private String readLsbRelease() {
        String family = null;
        if (new File("/etc/lsb-release").exists()) {
            List<String> osRelease = Builder.readFile("/etc/lsb-release");
            // Search for NAME=
            for (String line : osRelease) {
                if (line.startsWith("DISTRIB_DESCRIPTION=")) {
                    Logger.debug("lsb-release: {}", line);
                    line = line.replace("DISTRIB_DESCRIPTION=", Normal.EMPTY).replaceAll("^\"|\"$", "").trim();
                    if (line.contains(" release ")) {
                        family = parseRelease(line, " release ");
                    }
                } else if (line.startsWith("DISTRIB_ID=") && family == null) {
                    Logger.debug("lsb-release: {}", line);
                    family = line.replace("DISTRIB_ID=", "").replaceAll("^\"|\"$", "").trim();
                } else if (line.startsWith("DISTRIB_RELEASE=") && this.versionId == null) {
                    Logger.debug("lsb-release: {}", line);
                    this.versionId = line.replace("DISTRIB_RELEASE=", "").replaceAll("^\"|\"$", "").trim();
                } else if (line.startsWith("DISTRIB_CODENAME=") && this.codeName == null) {
                    Logger.debug("lsb-release: {}", line);
                    this.codeName = line.replace("DISTRIB_CODENAME=", "").replaceAll("^\"|\"$", "").trim();
                }
            }
        }
        return family;
    }

    /**
     * Attempts to read /etc/distrib-release (for some value of distrib)
     *
     * @return true if file successfully read and " release " or " VERSION " found
     */
    private String readDistribRelease(String filename) {
        String family = null;
        if (new File(filename).exists()) {
            List<String> osRelease = Builder.readFile(filename);
            // Search for Distrib release x.x (Codename)
            for (String line : osRelease) {
                Logger.debug("{}: {}", filename, line);
                if (line.contains(" release ")) {
                    family = parseRelease(line, " release ");
                    // If this parses properly we're done
                    break;
                } else if (line.contains(" VERSION ")) {
                    family = parseRelease(line, " VERSION ");
                    // If this parses properly we're done
                    break;
                }
            }
        }
        return family;
    }

    /**
     * Helper method to parse version description line style
     *
     * @param line      a String of the form "Distributor release x.x (Codename)"
     * @param splitLine A regex to split on, e.g. " release "
     * @return the parsed family (versionID and codeName may have also been set)
     */
    private String parseRelease(String line, String splitLine) {
        String[] split = line.split(splitLine);
        String family = split[0].trim();
        if (split.length > 1) {
            split = split[1].split("[()]");
            if (split.length > 0) {
                this.versionId = split[0].trim();
            }
            if (split.length > 1) {
                this.codeName = split[1].trim();
            }
        }
        return family;
    }

    @Override
    public OSService[] getServices() {
        // Get running services
        List<OSService> services = new ArrayList<>();
        Set<String> running = new HashSet<>();
        for (OSProcess p : getChildProcesses(1, 0, ProcessSort.PID)) {
            OSService s = new OSService(p.getName(), p.getProcessID(), OSService.State.RUNNING);
            services.add(s);
            running.add(p.getName());
        }
        // Get Directories for stopped services
        File dir = new File("/etc/init");
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles((f, name) -> name.toLowerCase().endsWith(".conf"))) {
                // remove .conf extension
                String name = f.getName().substring(0, f.getName().length() - 5);
                int index = name.lastIndexOf(Symbol.C_DOT);
                String shortName = (index < 0 || index > name.length() - 2) ? name : name.substring(index + 1);
                if (!running.contains(name) && !running.contains(shortName)) {
                    OSService s = new OSService(name, 0, OSService.State.STOPPED);
                    services.add(s);
                }
            }
        } else {
            Logger.error("Directory: /etc/init does not exist");
        }
        return services.toArray(new OSService[0]);
    }

    // Order the field is in /proc/pid/stat
    enum ProcPidStat {
        // The parsing implementation in ParseUtil requires these to be declared
        // in increasing order
        PPID(4), USER_TIME(14), KERNEL_TIME(15), PRIORITY(18), THREAD_COUNT(20), START_TIME(22), VSZ(23), RSS(24);

        private int order;

        ProcPidStat(int order) {
            this.order = order;
        }

        public int getOrder() {
            return this.order;
        }
    }
}
