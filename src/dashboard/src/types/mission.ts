// Định nghĩa kiểu dữ liệu phục vụ F15 (Nhiệm vụ bay UAV) và F17 (Đồng bộ ngoại tuyến)

export type MissionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'SCHEDULED' | 'FAILED';

// 3 trạng thái đồng bộ ngoại tuyến cốt lõi của F17
export type DataSyncState =
    | 'SYNCED_BACKEND'     // Đã nạp thành công vào Spring Boot Backend
    | 'COLLECTED_OFFLINE'  // UAV đã thu thập qua LoRa, đang lưu tạm trên máy bay
    | 'PENDING_COLLECTION'; // Cảm biến đã đo nhưng UAV chưa bay qua thu thập

export interface MissionNodeProgress {
    nodeId: string;
    nodeName: string;
    zoneName: string;
    syncState: DataSyncState;
    loraRssi?: number;       // Cường độ tín hiệu LoRa (dBm) khi UAV bay qua thu nhận
    collectedAt?: string;    // Giờ UAV thu nhận qua LoRa
    syncedToBackendAt?: string; // Giờ nạp lên server trung tâm
    recordsCount: number;    // Số lượng gói tin/bản ghi đo
}

export interface UavMission {
    id: string;              // Mã nhiệm vụ (vd: MSN-2026-001)
    flightCode: string;      // Tên hiệu chuyến bay (vd: FLIGHT-ALPHA-01)
    uavId: string;           // Mã thiết bị drone (vd: UAV-QUAD-01)
    status: MissionStatus;
    targetZone: string;      // Khu vực bay quét
    totalNodes: number;      // Tổng số trạm cảm biến trong lộ trình
    collectedNodes: number;  // Số trạm đã thu thập được
    batteryStart: number;    // Dung lượng pin khi cất cánh (%)
    batteryCurrent: number;  // Dung lượng pin hiện tại (%)
    startedAt?: string;
    completedAt?: string;
    altitudeMeters: number;  // Độ cao bay (m)
    speedMs: number;         // Tốc độ bay (m/s)
    nodeDetails: MissionNodeProgress[];
}