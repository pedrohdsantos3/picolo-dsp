#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <sstream>
#include <string>
#include <vector>

#include <libusb.h>

namespace {

struct IsoTransferState {
    bool completed = false;
};

void onIsoTransferComplete(libusb_transfer* transfer) {
    auto* state = static_cast<IsoTransferState*>(transfer->user_data);
    state->completed = true;
}

std::string errorText(int result) {
    std::ostringstream out;
    out << libusb_error_name(result) << " (" << libusb_strerror(result) << ")";
    return out.str();
}

std::string probeCaptureEndpoint(
        libusb_context* context,
        libusb_device_handle* handle,
        libusb_device* device,
        int interfaceNumber,
        int alternateSetting,
        const libusb_endpoint_descriptor& endpoint) {
    std::ostringstream out;
    const int claimResult = libusb_claim_interface(handle, interfaceNumber);
    if (claimResult != LIBUSB_SUCCESS) {
        out << "    interface " << interfaceNumber << " claim failed: "
            << errorText(claimResult) << "\n";
        return out.str();
    }
    out << "    interface " << interfaceNumber << " claimed\n";

    const int alternateResult = libusb_set_interface_alt_setting(
            handle, interfaceNumber, alternateSetting);
    if (alternateResult != LIBUSB_SUCCESS) {
        out << "    alternate setting " << alternateSetting << " failed: "
            << errorText(alternateResult) << "\n";
        libusb_release_interface(handle, interfaceNumber);
        return out.str();
    }
    out << "    alternate setting " << alternateSetting << " selected\n";

    const int packetSize = libusb_get_max_alt_packet_size(
            device, interfaceNumber, alternateSetting, endpoint.bEndpointAddress);
    if (packetSize <= 0) {
        out << "    cannot determine isochronous packet size: "
            << (packetSize < 0 ? errorText(packetSize) : "zero packet size") << "\n";
        libusb_set_interface_alt_setting(handle, interfaceNumber, 0);
        libusb_release_interface(handle, interfaceNumber);
        return out.str();
    }

    constexpr int kPacketsPerTransfer = 8;
    std::vector<unsigned char> buffer(
            static_cast<size_t>(packetSize) * kPacketsPerTransfer, 0);
    IsoTransferState state;
    libusb_transfer* transfer = libusb_alloc_transfer(kPacketsPerTransfer);
    if (!transfer) {
        out << "    cannot allocate isochronous transfer\n";
        libusb_set_interface_alt_setting(handle, interfaceNumber, 0);
        libusb_release_interface(handle, interfaceNumber);
        return out.str();
    }

    libusb_fill_iso_transfer(
            transfer,
            handle,
            endpoint.bEndpointAddress,
            buffer.data(),
            static_cast<int>(buffer.size()),
            kPacketsPerTransfer,
            onIsoTransferComplete,
            &state,
            0);
    libusb_set_iso_packet_lengths(transfer, static_cast<unsigned int>(packetSize));

    const int submitResult = libusb_submit_transfer(transfer);
    if (submitResult != LIBUSB_SUCCESS) {
        out << "    isochronous submit failed: " << errorText(submitResult) << "\n";
        libusb_free_transfer(transfer);
        libusb_set_interface_alt_setting(handle, interfaceNumber, 0);
        libusb_release_interface(handle, interfaceNumber);
        return out.str();
    }

    const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(2);
    while (!state.completed && std::chrono::steady_clock::now() < deadline) {
        timeval timeout{};
        timeout.tv_sec = 0;
        timeout.tv_usec = 100000;
        const int eventResult = libusb_handle_events_timeout_completed(
                context, &timeout, nullptr);
        if (eventResult != LIBUSB_SUCCESS && eventResult != LIBUSB_ERROR_INTERRUPTED) {
            out << "    USB event handling failed: " << errorText(eventResult) << "\n";
            break;
        }
    }

    if (!state.completed) {
        libusb_cancel_transfer(transfer);
        while (!state.completed) {
            timeval timeout{};
            timeout.tv_sec = 0;
            timeout.tv_usec = 100000;
            libusb_handle_events_timeout_completed(context, &timeout, nullptr);
        }
        out << "    isochronous transfer timed out\n";
    } else {
        int successfulPackets = 0;
        int packetsWithData = 0;
        for (int i = 0; i < transfer->num_iso_packets; ++i) {
            const auto& packet = transfer->iso_packet_desc[i];
            if (packet.status == LIBUSB_TRANSFER_COMPLETED) {
                ++successfulPackets;
                if (packet.actual_length > 0) ++packetsWithData;
            }
        }
        out << "    isochronous IN completed: " << successfulPackets << "/"
            << transfer->num_iso_packets << " packets; " << packetsWithData
            << " carried data\n";
        if (transfer->status != LIBUSB_TRANSFER_COMPLETED) {
            out << "    transfer status: " << transfer->status << "\n";
        }
    }

    libusb_free_transfer(transfer);
    libusb_set_interface_alt_setting(handle, interfaceNumber, 0);
    libusb_release_interface(handle, interfaceNumber);
    return out.str();
}

std::string probeUsbAudio(int fileDescriptor) {
    if (fileDescriptor < 0) return "USB permission connection has no file descriptor.";

    const libusb_init_option options[] = {
            {LIBUSB_OPTION_NO_DEVICE_DISCOVERY, {.ival = 1}},
    };
    libusb_context* context = nullptr;
    int result = libusb_init_context(&context, options, 1);
    if (result != LIBUSB_SUCCESS) {
        return "libusb initialization failed: " + errorText(result);
    }

    libusb_device_handle* handle = nullptr;
    result = libusb_wrap_sys_device(
            context, static_cast<intptr_t>(fileDescriptor), &handle);
    if (result != LIBUSB_SUCCESS) {
        libusb_exit(context);
        return "Android USB file descriptor could not be opened by libusb: " + errorText(result);
    }

    std::ostringstream out;
    libusb_device* device = libusb_get_device(handle);
    libusb_device_descriptor deviceDescriptor{};
    result = libusb_get_device_descriptor(device, &deviceDescriptor);
    if (result != LIBUSB_SUCCESS) {
        out << "USB descriptor read failed: " << errorText(result) << "\n";
    } else {
        out << "USB device " << std::hex << deviceDescriptor.idVendor << ":"
            << deviceDescriptor.idProduct << std::dec << "\n";
    }

    libusb_config_descriptor* config = nullptr;
    result = libusb_get_active_config_descriptor(device, &config);
    if (result != LIBUSB_SUCCESS) {
        result = libusb_get_config_descriptor(device, 0, &config);
    }
    if (result != LIBUSB_SUCCESS || !config) {
        out << "USB configuration read failed: " << errorText(result) << "\n";
        libusb_close(handle);
        libusb_exit(context);
        return out.str();
    }

    out << "Interfaces: " << static_cast<int>(config->bNumInterfaces) << "\n";
    const int detachResult = libusb_set_auto_detach_kernel_driver(handle, 1);
    out << "Kernel-driver auto-detach: "
        << (detachResult == LIBUSB_SUCCESS ? "enabled" : errorText(detachResult)) << "\n";

    bool foundAudioStreaming = false;
    bool foundIsochronousCapture = false;
    for (uint8_t i = 0; i < config->bNumInterfaces; ++i) {
        const libusb_interface& usbInterface = config->interface[i];
        for (int a = 0; a < usbInterface.num_altsetting; ++a) {
            const libusb_interface_descriptor& alt = usbInterface.altsetting[a];
            if (alt.bInterfaceClass != LIBUSB_CLASS_AUDIO || alt.bInterfaceSubClass != 2) {
                continue;
            }
            foundAudioStreaming = true;
            out << "AudioStreaming interface " << static_cast<int>(alt.bInterfaceNumber)
                << " alt=" << static_cast<int>(alt.bAlternateSetting)
                << " endpoints=" << static_cast<int>(alt.bNumEndpoints) << "\n";

            if (alt.bAlternateSetting == 0) continue;
            for (uint8_t e = 0; e < alt.bNumEndpoints; ++e) {
                const libusb_endpoint_descriptor& endpoint = alt.endpoint[e];
                const bool isIsochronous =
                        (endpoint.bmAttributes & LIBUSB_TRANSFER_TYPE_MASK) ==
                        LIBUSB_TRANSFER_TYPE_ISOCHRONOUS;
                const bool isInput = (endpoint.bEndpointAddress & LIBUSB_ENDPOINT_DIR_MASK) != 0;
                if (!isIsochronous || !isInput) continue;

                foundIsochronousCapture = true;
                out << "  capture endpoint 0x" << std::hex
                    << static_cast<int>(endpoint.bEndpointAddress) << std::dec
                    << " maxPacket=" << endpoint.wMaxPacketSize
                    << " interval=" << static_cast<int>(endpoint.bInterval) << "\n";
                out << probeCaptureEndpoint(
                        context, handle, device, alt.bInterfaceNumber,
                        alt.bAlternateSetting, endpoint);
                break;
            }
            if (foundIsochronousCapture) break;
        }
        if (foundIsochronousCapture) break;
    }

    if (!foundAudioStreaming) out << "No USB Audio Streaming interface found.\n";
    else if (!foundIsochronousCapture) out << "No isochronous capture endpoint found.\n";

    libusb_free_config_descriptor(config);
    libusb_close(handle);
    libusb_exit(context);
    out << "\nThis probe checks USB access and capture isochronous transfers; it does not start audio playback.\n";
    return out.str();
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_pedro_tone3000m1_NativeAudioEngine_nativeProbeUsbAudio(
        JNIEnv* env,
        jobject,
        jint fileDescriptor) {
    const std::string result = probeUsbAudio(fileDescriptor);
    return env->NewStringUTF(result.c_str());
}
