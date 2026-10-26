#define _GNU_SOURCE
#define _FILE_OFFSET_BITS 64

#include <gio/gio.h>
#include <gio/gunixfdlist.h>
#include <errno.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <linux/fs.h>
#include <unistd.h>

#define UDISKS_NAME "org.freedesktop.UDisks2"
#define BLOCK_IFACE "org.freedesktop.UDisks2.Block"

static int command_has_output(const char *cmd) {
    FILE *p = popen(cmd, "r");
    if (!p) return -1;

    char line[512];
    int found = 0;

    while (fgets(line, sizeof line, p)) {
        if (line[0] != '\n' && line[0] != '\0') found = 1;
    }

    int status = pclose(p);
    if (status == -1 || !WIFEXITED(status) || WEXITSTATUS(status) != 0)
        return -1;

    return found;
}

static int safe_to_open(const char *canonical) {
    /*
     * Reject any device whose block-device tree includes the root disk.
     * lsblk reports the requested device and all its descendants.
     */
    FILE *p = popen("findmnt -n -o SOURCE /", "r");
    if (!p) {
        fprintf(stderr, "Cannot determine root filesystem device.\n");
        return 0;
    }

    char buf[512];
    if (!fgets(buf, sizeof buf, p)) {
        pclose(p);
        fprintf(stderr, "Cannot read root filesystem source.\n");
        return 0;
    }
    int status = pclose(p);
    if (status == -1 || !WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        fprintf(stderr, "Root filesystem inspection failed.\n");
        return 0;
    }

    buf[strcspn(buf, "\r\n")] = '\0';
    char *root_real = realpath(buf, NULL);
    if (!root_real) {
        fprintf(stderr, "Cannot resolve root filesystem source: %s\n", buf);
        return 0;
    }

    /*
     * Compare block-device ancestry by major/minor numbers.
     * This also rejects a whole disk containing the root partition.
     */
    char *cmd = NULL;
    if (asprintf(&cmd, "lsblk -nrpo MAJ:MIN %s", canonical) < 0) {
        free(root_real);
        return 0;
    }

    struct stat root_st;
    if (stat(root_real, &root_st) != 0) {
        fprintf(stderr, "Cannot stat root device.\n");
        free(root_real);
        free(cmd);
        return 0;
    }

    char root_mm[64];
    snprintf(root_mm, sizeof root_mm, "%u:%u",
             major(root_st.st_rdev), minor(root_st.st_rdev));

    p = popen(cmd, "r");
    free(cmd);
    if (!p) {
        fprintf(stderr, "Cannot inspect block-device ancestry.\n");
        free(root_real);
        return 0;
    }

    int root_match = 0;
    while (fgets(buf, sizeof buf, p)) {
        buf[strcspn(buf, "\r\n")] = '\0';
        if (strcmp(buf, root_mm) == 0) root_match = 1;
    }

    status = pclose(p);
    free(root_real);
    if (status == -1 || !WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        fprintf(stderr, "Block-device ancestry check failed.\n");
        return 0;
    }
    if (root_match) {
        fprintf(stderr, "Refusing root/system disk.\n");
        return 0;
    }

    /*
     * Reject the requested device if it or any descendant is mounted.
     * lsblk's MOUNTPOINT column is empty for unmounted devices.
     */
    if (asprintf(&cmd, "lsblk -nrpo MOUNTPOINT %s", canonical) < 0)
        return 0;

    int mounts = command_has_output(cmd);
    free(cmd);

    if (mounts != 0) {
        fprintf(stderr, "Refusing: mounted filesystem or mount check error.\n");
        return 0;
    }

    return 1;
}

static int open_readonly(const char *path, uint64_t *capacity) {
    char *canonical = realpath(path, NULL);
    struct stat original;

    if (!canonical || strncmp(canonical, "/dev/", 5) != 0 ||
        stat(canonical, &original) != 0 || !S_ISBLK(original.st_mode)) {
        fprintf(stderr, "Refusing: target is not a valid block device.\n");
        free(canonical);
        return -1;
    }

    const char *device_name = strrchr(canonical, '/');
    device_name = device_name ? device_name + 1 : canonical;
    if (*device_name == '\0') {
        fprintf(stderr, "Refusing empty device basename.\n");
        free(canonical);
        return -1;
    }
    for (const char *p = device_name; *p; ++p) {
        if (!( (*p >= 'a' && *p <= 'z') ||
               (*p >= 'A' && *p <= 'Z') ||
               (*p >= '0' && *p <= '9') ||
               *p == '_' || *p == '-' || *p == '.' )) {
            fprintf(stderr, "Refusing unusual device basename.\n");
            free(canonical);
            return -1;
        }
    }

    if (!safe_to_open(canonical)) {
        free(canonical);
        return -1;
    }

    gchar *base = strrchr(canonical, '/');
    base = base ? base + 1 : canonical;
    gchar *object_path = g_strdup_printf(
        "/org/freedesktop/UDisks2/block_devices/%s", base);

    GError *error = NULL;
    GDBusConnection *bus =
        g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, &error);

    if (!bus) {
        fprintf(stderr, "System D-Bus unavailable: %s\n",
                error ? error->message : "unknown error");
        g_clear_error(&error);
        g_free(object_path);
        free(canonical);
        return -1;
    }

    GUnixFDList *fd_list = NULL;
    GVariant *reply = g_dbus_connection_call_with_unix_fd_list_sync(
        bus, UDISKS_NAME, object_path, BLOCK_IFACE, "OpenDevice",
        g_variant_new("(s@a{sv})", "r",
            g_variant_new_array(G_VARIANT_TYPE("{sv}"), NULL, 0)),
        G_VARIANT_TYPE("(h)"), G_DBUS_CALL_FLAGS_NONE, 10000,
        NULL, &fd_list, NULL, &error);

    int fd = -1;
    if (!reply) {
        fprintf(stderr, "Read-only OpenDevice failed: %s\n",
                error ? error->message : "unknown error");
        g_clear_error(&error);
        goto cleanup;
    }

    gint handle = -1;
    g_variant_get(reply, "(h)", &handle);

    if (!fd_list) {
        fprintf(stderr, "UDisks2 returned no descriptor list.\n");
        goto cleanup;
    }

    fd = g_unix_fd_list_get(fd_list, handle, &error);
    if (fd < 0) {
        fprintf(stderr, "Could not obtain device descriptor: %s\n",
                error ? error->message : "unknown error");
        g_clear_error(&error);
        goto cleanup;
    }

    struct stat opened;
    uint64_t bytes = 0;

    if (fstat(fd, &opened) != 0 || !S_ISBLK(opened.st_mode) ||
        opened.st_rdev != original.st_rdev ||
        ioctl(fd, BLKGETSIZE64, &bytes) != 0 || bytes == 0) {
        fprintf(stderr, "Opened descriptor identity/size check failed.\n");
        close(fd);
        fd = -1;
        goto cleanup;
    }

    *capacity = bytes;
    fprintf(stderr, "Read-only descriptor verified: %s (%" PRIu64 " bytes)\n",
           canonical, bytes);

cleanup:
    if (reply) g_variant_unref(reply);
    if (fd_list) g_object_unref(fd_list);
    g_object_unref(bus);
    g_free(object_path);
    free(canonical);
    return fd;
}

static int read_exact_at(int fd, uint64_t offset, uint32_t length,
                         uint64_t capacity) {
    if (length == 0 || length > 1024 * 1024 ||
        offset > capacity || (uint64_t)length > capacity - offset ||
        offset > (uint64_t)INT64_MAX) {
        fprintf(stderr, "Rejected invalid READ range.\n");
        return 0;
    }

    uint8_t buffer[65536];
    uint32_t remaining = length;
    uint64_t position = offset;

    printf("DATA %" PRIu32 "\n", length);
    fflush(stdout);

    while (remaining > 0) {
        size_t chunk = remaining < sizeof buffer ? remaining : sizeof buffer;
        ssize_t got = pread(fd, buffer, chunk, (off_t)position);

        if (got < 0 && errno == EINTR) continue;
        if (got <= 0) {
            fprintf(stderr, "Read failed or ended unexpectedly.\n");
            return 0;
        }

        if (fwrite(buffer, 1, (size_t)got, stdout) != (size_t)got ||
            fflush(stdout) != 0) {
            fprintf(stderr, "Output stream failed.\n");
            return 0;
        }

        position += (uint64_t)got;
        remaining -= (uint32_t)got;
    }

    return 1;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "Usage: %s /dev/DEVICE\n", argv[0]);
        return 2;
    }

    uint64_t capacity = 0;
    int fd = open_readonly(argv[1], &capacity);
    if (fd < 0) return 1;

    char *line = NULL;
    size_t line_capacity = 0;
    int exit_code = 0;

    while (getline(&line, &line_capacity, stdin) >= 0) {
        line[strcspn(line, "\r\n")] = '\0';

        if (strcmp(line, "SIZE") == 0) {
            printf("SIZE %" PRIu64 "\n", capacity);
            fflush(stdout);
        } else if (strcmp(line, "QUIT") == 0) {
            puts("OK");
            fflush(stdout);
            break;
        } else if (strncmp(line, "READ ", 5) == 0) {
            char *end = NULL;
            errno = 0;
            uint64_t offset = strtoull(line + 5, &end, 10);

            if (errno || end == line + 5 || *end != ' ') {
                puts("ERR invalid-offset");
                fflush(stdout);
                continue;
            }

            char *length_text = end + 1;
            errno = 0;
            unsigned long length = strtoul(length_text, &end, 10);

            if (errno || end == length_text || *end != '\0' ||
                length == 0 || length > 1024 * 1024) {
                puts("ERR invalid-length");
                fflush(stdout);
                continue;
            }

            if (offset > capacity || (uint64_t)length > capacity - offset) {
                puts("ERR out-of-range");
                fflush(stdout);
                continue;
            }

            if (!read_exact_at(fd, offset, (uint32_t)length, capacity)) {
                exit_code = 1;
                break;
            }
        } else {
            puts("ERR unknown-command");
            fflush(stdout);
        }
    }

    free(line);
    close(fd);
    return exit_code;
}
