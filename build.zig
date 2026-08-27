const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{
        .default_target = .{
            .cpu_arch = .x86_64,
            .os_tag = .windows,
            .abi = .gnu,
        },
    });
    const optimize = b.standardOptimizeOption(.{});

    const mod = b.createModule(.{
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });

    mod.addIncludePath(b.path("src"));
    mod.addIncludePath(b.path("third_party/minhook/include"));

    mod.addCSourceFiles(.{
        .files = &.{
            "src/main.c",
            "src/ccp.c",
            "src/rsapatch.c",
            "src/pattern_scan.c",
            "third_party/minhook/src/hook.c",
            "third_party/minhook/src/buffer.c",
            "third_party/minhook/src/trampoline.c",
            "third_party/minhook/src/hde/hde32.c",
            "third_party/minhook/src/hde/hde64.c",
        },
        .flags = &.{ "-std=c11", "-Wall" },
    });

    mod.linkSystemLibrary("kernel32", .{});
    mod.linkSystemLibrary("user32", .{});

    const dll = b.addLibrary(.{
        .name = "hipa-patch",
        .linkage = .dynamic,
        .root_module = mod,
    });

    b.installArtifact(dll);
}
