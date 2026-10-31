package emu.grasscutter.tools.protocol;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Regenerates Java protocol sources from the canonical 7.1 descriptor set and recovered additions. */
public final class ProtocolJavaGenerator {
    private static final int FILES_PER_PROTOC_INVOCATION = 128;

    private ProtocolJavaGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6 && args.length != 8) {
            throw new IllegalArgumentException(
                    "usage: ProtocolJavaGenerator <protoc> <descriptor-set> <file-list>"
                        + " <supplemental-proto-dir> <supplemental-import-dir> <java-out>"
                        + " [audit-descriptor-out generator-source-dir]");
        }

        var protoc = Path.of(args[0]).toAbsolutePath().normalize();
        var descriptorSet = Path.of(args[1]).toAbsolutePath().normalize();
        var fileList = Path.of(args[2]).toAbsolutePath().normalize();
        var supplementalProtoDir = Path.of(args[3]).toAbsolutePath().normalize();
        var supplementalImportDir = Path.of(args[4]).toAbsolutePath().normalize();
        var javaOut = Path.of(args[5]).toAbsolutePath().normalize();
        var auditOut = args.length == 8 ? Path.of(args[6]).toAbsolutePath().normalize() : null;
        var generatorSourceDir =
                args.length == 8 ? Path.of(args[7]).toAbsolutePath().normalize() : null;
        if (auditOut != null) {
            if (javaOut.startsWith(generatorSourceDir) || generatorSourceDir.startsWith(javaOut)) {
                throw new IllegalArgumentException("Generator sources and Java output must not overlap");
            }
            if (auditOut.equals(protoc)
                    || auditOut.startsWith(generatorSourceDir)
                    || auditOut.equals(descriptorSet)
                    || auditOut.equals(fileList)
                    || auditOut.startsWith(javaOut)
                    || auditOut.startsWith(supplementalProtoDir)
                    || auditOut.startsWith(supplementalImportDir)) {
                throw new IllegalArgumentException(
                        "Audit output must not overwrite protocol inputs or Java output");
            }
            Files.deleteIfExists(auditOut);
            Files.deleteIfExists(Path.of(auditOut + ".json"));
        }

        requireRegularFile(protoc, "protoc executable");
        requireRegularFile(descriptorSet, "descriptor set");
        requireRegularFile(fileList, "protocol file list");
        requireDirectory(supplementalProtoDir, "supplemental proto directory");
        requireDirectory(supplementalImportDir, "supplemental import stub directory");
        var generatorSourcesHash =
                generatorSourceDir == null ? null : generatorFingerprint(generatorSourceDir);

        var protocolFiles = Files.readAllLines(fileList, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .distinct()
                .sorted()
                .toList();
        if (protocolFiles.isEmpty()) {
            throw new IllegalStateException("protocol file list is empty: " + fileList);
        }

        var supplementalProtoFiles = listProtoFiles(supplementalProtoDir);
        var allProtocolFiles = new TreeSet<String>();
        allProtocolFiles.addAll(protocolFiles);
        allProtocolFiles.addAll(supplementalProtoFiles);
        var overlappingFiles = protocolFiles.stream().filter(supplementalProtoFiles::contains).toList();
        var expectedJavaFileCount = allProtocolFiles.size();

        recreateDirectory(javaOut);

        Path temporaryDirectory = null;
        byte[] auditDescriptor = null;
        try {
            temporaryDirectory = Files.createTempDirectory("astaps-protocol-descriptors-");
            var supplementalDescriptorSet = temporaryDirectory.resolve("supplemental.desc");
            var combinedDescriptorSet = temporaryDirectory.resolve("combined.desc");

            FileDescriptorSet supplementalDescriptors = FileDescriptorSet.getDefaultInstance();
            if (!supplementalProtoFiles.isEmpty()) {
                runSupplementalDescriptorProtoc(
                        protoc,
                        supplementalProtoDir,
                        supplementalImportDir,
                        supplementalDescriptorSet,
                        supplementalProtoFiles);
                supplementalDescriptors =
                        FileDescriptorSet.parseFrom(Files.readAllBytes(supplementalDescriptorSet));
            }

            var baseDescriptors = FileDescriptorSet.parseFrom(Files.readAllBytes(descriptorSet));
            var combinedDescriptors = mergeAndPatchDescriptors(baseDescriptors, supplementalDescriptors);
            auditDescriptor = combinedDescriptors.toByteArray();
            Files.write(combinedDescriptorSet, auditDescriptor);

            var filesToGenerate = List.copyOf(allProtocolFiles);
            for (int offset = 0; offset < filesToGenerate.size(); offset += FILES_PER_PROTOC_INVOCATION) {
                var end = Math.min(offset + FILES_PER_PROTOC_INVOCATION, filesToGenerate.size());
                runDescriptorProtoc(
                        protoc, combinedDescriptorSet, javaOut, filesToGenerate.subList(offset, end));
            }
        } finally {
            if (temporaryDirectory != null) {
                deleteDirectory(temporaryDirectory);
            }
        }

        long javaFileCount;
        try (Stream<Path> generatedFiles = Files.walk(javaOut)) {
            javaFileCount = generatedFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .count();
        }
        if (javaFileCount != expectedJavaFileCount) {
            throw new IllegalStateException(
                    "protoc generated "
                            + javaFileCount
                            + " Java files; expected "
                            + expectedJavaFileCount
                            + " from "
                            + protocolFiles.size()
                            + " base descriptor files and "
                            + supplementalProtoFiles.size()
                            + " supplemental proto files ("
                            + overlappingFiles.size()
                            + " overrides)");
        }

        if (auditOut != null) {
            Files.createDirectories(auditOut.getParent());
            Files.write(auditOut, auditDescriptor);
            String metadata =
                    "{\"format\":\"astaps.server-protocol-descriptor.v1\","
                            + "\"descriptor_sha256\":\""
                            + sha256(auditDescriptor)
                            + "\","
                            + "\"base_sha256\":\""
                            + sha256(Files.readAllBytes(descriptorSet))
                            + "\","
                            + "\"proto_sources_sha256\":\""
                            + sourceFingerprint(supplementalProtoDir, supplementalImportDir)
                            + "\","
                            + "\"generator_sources_sha256\":\""
                            + generatorSourcesHash
                            + "\"}\n";
            Files.writeString(Path.of(auditOut + ".json"), metadata, StandardCharsets.UTF_8);
        }

        System.out.printf(
                "Generated %,d Java files from %,d base descriptors and %,d supplemental proto files (%,d overrides).%n",
                javaFileCount,
                protocolFiles.size(),
                supplementalProtoFiles.size(),
                overlappingFiles.size());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String generatorFingerprint(Path root) throws Exception {
        requireDirectory(root, "generator source directory");
        var rows = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java")).toList()) {
                rows.add(root.relativize(path).toString().replace(File.separatorChar, '/')
                        + "\0" + sha256(Files.readAllBytes(path)) + "\n");
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Generator source directory contains no Java files: " + root);
        }
        return sha256(String.join("", rows).getBytes(StandardCharsets.UTF_8));
    }

    private static String sourceFingerprint(Path protoDir, Path importDir) throws Exception {
        var rows = new TreeSet<String>();
        for (int i = 0; i < 2; i++) {
            Path root = i == 0 ? protoDir : importDir;
            String prefix = i == 0 ? "compat/" : "compat-imports/";
            for (String name : listProtoFiles(root)) {
                rows.add(
                        prefix
                                + name
                                + "\0"
                                + sha256(Files.readAllBytes(root.resolve(name)))
                                + "\n");
            }
        }
        return sha256(String.join("", rows).getBytes(StandardCharsets.UTF_8));
    }

    private static FileDescriptorSet mergeAndPatchDescriptors(
            FileDescriptorSet base, FileDescriptorSet supplemental) {
        var filesByName = new LinkedHashMap<String, FileDescriptorProto>();
        for (var file : base.getFileList()) {
            filesByName.put(file.getName(), file);
        }
        for (var file : supplemental.getFileList()) {
            filesByName.put(file.getName(), file);
        }

        var result = FileDescriptorSet.newBuilder();
        for (var file : filesByName.values()) {
            result.addFile(applyRecoveredProtocolPatches(file));
        }
        return result.build();
    }

    /**
     * The canonical descriptor dump predates semantic recovery of several TPS fields. Their wire
     * slots already exist under obfuscated names. Validate each legacy shape before replacing only
     * the field descriptor, preserving every other recovered field unchanged.
     */
    private static FileDescriptorProto applyRecoveredProtocolPatches(FileDescriptorProto file) {
        return switch (file.getName()) {
            case "BlossomBriefInfoNotify.proto" -> patchBlossomBriefListField(file);
            case "AvatarInfo.proto" ->
                    replaceMessageField(
                            file,
                            "AvatarInfo",
                            "KPJFLNKEFBC",
                            ".SceneWeaponInfo",
                            repeatedMessageField("tps_weapon_list", 37, ".SceneWeaponInfo"),
                            "SceneWeaponInfo.proto");
            case "SceneAvatarInfo.proto" ->
                    replaceMessageField(
                            file,
                            "SceneAvatarInfo",
                            "KPJFLNKEFBC",
                            ".SceneWeaponInfo",
                            repeatedMessageField("tps_weapon_list", 31, ".SceneWeaponInfo"),
                            "SceneWeaponInfo.proto");
            case "SceneTeamAvatar.proto" ->
                    replaceMessageField(
                            file,
                            "SceneTeamAvatar",
                            "DGGJCMNHDOJ",
                            ".SceneWeaponInfo",
                            repeatedMessageField("tps_weapon_list", 382, ".SceneWeaponInfo"),
                            "SceneWeaponInfo.proto");
            case "AvatarEnterSceneInfo.proto" ->
                    replaceMessageField(
                            file,
                            "AvatarEnterSceneInfo",
                            "DGGJCMNHDOJ",
                            ".SceneWeaponInfo",
                            repeatedMessageField("tps_weapon_list", 13, ".SceneWeaponInfo"),
                            "SceneWeaponInfo.proto");
            case "SceneWeaponInfo.proto" ->
                    replaceMessageField(
                            file,
                            "SceneWeaponInfo",
                            "KCLFBBACHLP",
                            ".IMPFHAGJHCE",
                            repeatedMessageField(
                                    "ammunition_list", 12, ".TpsWeaponAmmunitionInfo"),
                            "TpsWeaponAmmunitionInfo.proto");
            case "_TpsWeapon.proto" ->
                    replaceMessageField(
                            file,
                            "_TpsWeapon",
                            "OMDPJNIPFDL",
                            null,
                            FieldDescriptorProto.newBuilder()
                                    .setName("accessory_id_list")
                                    .setNumber(2)
                                    .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                                    .setType(FieldDescriptorProto.Type.TYPE_UINT32)
                                    .build());
            default -> file;
        };
    }

    /**
     * Global 7.1 original client: ABJOMOIIDHH (CmdId 6743) reads its repeated
     * MFOACFDAOID element from protobuf field 10 (tag 0x52). The imported
     * server descriptor has the corresponding field at 13.
     * See Genshin-Reverse/versions/7.1.0-global/windows-x64/analyses/blossom-map-markers.
     */
    private static FileDescriptorProto patchBlossomBriefListField(FileDescriptorProto file) {
        var fileBuilder = file.toBuilder();
        for (int i = 0; i < fileBuilder.getMessageTypeCount(); i++) {
            if (!fileBuilder.getMessageType(i).getName().equals("BlossomBriefInfoNotify")) continue;
            var message = fileBuilder.getMessageTypeBuilder(i);
            for (int j = 0; j < message.getFieldCount(); j++) {
                var field = message.getField(j);
                if (!field.getName().equals("brief_info_list")) continue;
                if (field.getNumber() != 13
                        || field.getLabel() != FieldDescriptorProto.Label.LABEL_REPEATED
                        || field.getType() != FieldDescriptorProto.Type.TYPE_MESSAGE
                        || !field.getTypeName().equals(".BlossomBriefInfo")) {
                    throw new IllegalStateException(
                            "Unexpected imported BlossomBriefInfoNotify.brief_info_list shape");
                }
                message.setField(j, field.toBuilder().setNumber(10).build());
                return fileBuilder.build();
            }
        }
        throw new IllegalStateException(
                "Cannot find imported BlossomBriefInfoNotify.brief_info_list in " + file.getName());
    }

    private static FieldDescriptorProto repeatedMessageField(
            String name, int number, String typeName) {
        return FieldDescriptorProto.newBuilder()
                .setName(name)
                .setNumber(number)
                .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(typeName)
                .build();
    }

    private static FileDescriptorProto replaceMessageField(
            FileDescriptorProto file,
            String messageName,
            String legacyName,
            String legacyTypeName,
            FieldDescriptorProto replacement,
            String... dependencies) {
        var fileBuilder = file.toBuilder();
        var messageIndex = -1;
        for (int index = 0; index < fileBuilder.getMessageTypeCount(); index++) {
            if (fileBuilder.getMessageType(index).getName().equals(messageName)) {
                messageIndex = index;
                break;
            }
        }
        if (messageIndex < 0) {
            throw new IllegalStateException(
                    "protocol patch target message " + messageName + " was not found in " + file.getName());
        }

        var messageBuilder = fileBuilder.getMessageTypeBuilder(messageIndex);
        var fieldIndex = -1;
        for (int index = 0; index < messageBuilder.getFieldCount(); index++) {
            var existing = messageBuilder.getField(index);
            if (existing.getNumber() == replacement.getNumber()) {
                fieldIndex = index;
                break;
            }
        }

        if (fieldIndex < 0) {
            throw new IllegalStateException(
                    "protocol patch field number "
                            + replacement.getNumber()
                            + " was not found in "
                            + file.getName()
                            + ":"
                            + messageName);
        }

        var existing = messageBuilder.getField(fieldIndex);
        if (existing.getName().equals(replacement.getName())) {
            validateReplacementShape(file, messageName, existing, replacement, replacement.getTypeName());
        } else {
            if (!existing.getName().equals(legacyName)) {
                throw new IllegalStateException(
                        "protocol patch expected legacy field "
                                + legacyName
                                + " at "
                                + file.getName()
                                + ":"
                                + messageName
                                + "#"
                                + replacement.getNumber()
                                + " but found "
                                + existing.getName());
            }
            validateReplacementShape(file, messageName, existing, replacement, legacyTypeName);
            messageBuilder.setField(fieldIndex, replacement);
        }

        for (var dependency : dependencies) {
            if (!fileBuilder.getDependencyList().contains(dependency)) {
                fileBuilder.addDependency(dependency);
            }
        }
        return fileBuilder.build();
    }

    private static void validateReplacementShape(
            FileDescriptorProto file,
            String messageName,
            FieldDescriptorProto existing,
            FieldDescriptorProto replacement,
            String expectedTypeName) {
        if (existing.getLabel() != replacement.getLabel() || existing.getType() != replacement.getType()) {
            throw new IllegalStateException(
                    "protocol patch wire shape mismatch at "
                            + file.getName()
                            + ":"
                            + messageName
                            + "#"
                            + replacement.getNumber()
                            + ": found "
                            + existing.getLabel()
                            + "/"
                            + existing.getType()
                            + ", expected "
                            + replacement.getLabel()
                            + "/"
                            + replacement.getType());
        }
        if (replacement.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE
                && !existing.getTypeName().equals(expectedTypeName)) {
            throw new IllegalStateException(
                    "protocol patch message type mismatch at "
                            + file.getName()
                            + ":"
                            + messageName
                            + "#"
                            + replacement.getNumber()
                            + ": found "
                            + existing.getTypeName()
                            + ", expected "
                            + expectedTypeName);
        }
    }

    private static List<String> listProtoFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".proto"))
                    .map(directory::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace(File.separatorChar, '/'))
                    .sorted()
                    .toList();
        }
    }

    private static void runSupplementalDescriptorProtoc(
            Path protoc,
            Path supplementalProtoDir,
            Path supplementalImportDir,
            Path supplementalDescriptorSet,
            List<String> supplementalProtoFiles)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>(supplementalProtoFiles.size() + 5);
        command.add(protoc.toString());
        command.add("--proto_path=" + supplementalProtoDir);
        command.add("--proto_path=" + supplementalImportDir);
        command.add("--descriptor_set_out=" + supplementalDescriptorSet);
        command.addAll(supplementalProtoFiles);

        runProtoc(command, "supplemental 7.1 descriptor recovery");
        requireRegularFile(supplementalDescriptorSet, "supplemental descriptor set");
    }

    private static void runDescriptorProtoc(
            Path protoc, Path descriptorSet, Path javaOut, List<String> protocolFiles)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>(protocolFiles.size() + 3);
        command.add(protoc.toString());
        command.add("--descriptor_set_in=" + descriptorSet);
        command.add("--java_out=" + javaOut);
        command.addAll(protocolFiles);

        runProtoc(command, protocolFiles.getFirst() + " .. " + protocolFiles.getLast());
    }

    private static void runProtoc(List<String> command, String description)
            throws IOException, InterruptedException {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "protoc failed with exit code "
                            + exitCode
                            + " while generating "
                            + description
                            + (output.isBlank() ? "" : ":\n" + output));
        }
        if (!output.isBlank()) {
            System.out.print(output);
        }
    }

    private static void recreateDirectory(Path directory) throws IOException {
        deleteDirectory(directory);
        Files.createDirectories(directory);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void requireRegularFile(Path path, String description) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(description + " was not found: " + path);
        }
    }

    private static void requireDirectory(Path path, String description) {
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException(description + " was not found: " + path);
        }
    }
}
