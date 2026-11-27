import type { UavMission } from '../types/mission';

export const MOCK_MISSIONS: UavMission[] = [
    {
        id: 'MSN-2026-001',
        flightCode: 'FLIGHT-ALPHA-01',
        uavId: 'UAV-AGRI-01 (Cánh bằng VTOL)',
        status: 'COMPLETED',
        targetZone: 'Khu Trồng Dâu Tây A & Khu Cà Phê Đồi',
        totalNodes: 3,
        collectedNodes: 3,
        batteryStart: 98,
        batteryCurrent: 76,
        startedAt: '2026-11-06T07:15:00',
        completedAt: '2026-11-06T07:45:00',
        altitudeMeters: 45,
        speedMs: 8.5,
        nodeDetails: [
            {
                nodeId: 'SN-A101',
                nodeName: 'Cảm biến Đất & Vi Khí Hậu 01',
                zoneName: 'Khu Trồng Dâu Tây A',
                syncState: 'SYNCED_BACKEND',
                loraRssi: -78,
                collectedAt: '2026-11-06T07:22:15',
                syncedToBackendAt: '2026-11-06T07:45:10',
                recordsCount: 48
            },
            {
                nodeId: 'SN-A102',
                nodeName: 'Cảm biến Đất 02 (Cảnh báo khô hạn)',
                zoneName: 'Khu Trồng Dâu Tây A',
                syncState: 'SYNCED_BACKEND',
                loraRssi: -82,
                collectedAt: '2026-11-06T07:26:40',
                syncedToBackendAt: '2026-11-06T07:45:12',
                recordsCount: 48
            },
            {
                nodeId: 'SN-W401',
                nodeName: 'Cảm biến Hồ Thủy Canh & pH',
                zoneName: 'Khu Thủy Canh Dinh Dưỡng',
                syncState: 'SYNCED_BACKEND',
                loraRssi: -71,
                collectedAt: '2026-11-06T07:34:05',
                syncedToBackendAt: '2026-11-06T07:45:18',
                recordsCount: 52
            }
        ]
    },
    {
        id: 'MSN-2026-002',
        flightCode: 'FLIGHT-BETA-02',
        uavId: 'UAV-AGRI-02 (Hexacopter)',
        status: 'IN_PROGRESS',
        targetZone: 'Khu Ươm Giống & Khu Sườn Đồi B',
        totalNodes: 3,
        collectedNodes: 1,
        batteryStart: 100,
        batteryCurrent: 84,
        startedAt: '2026-11-06T11:10:00',
        altitudeMeters: 35,
        speedMs: 6.2,
        nodeDetails: [
            {
                nodeId: 'SN-B201',
                nodeName: 'Cảm biến Cà Phê B (Vừa thu nhận LoRa)',
                zoneName: 'Khu Cà Phê Sườn Đồi',
                syncState: 'COLLECTED_OFFLINE',
                loraRssi: -85,
                collectedAt: '2026-11-06T11:18:22',
                recordsCount: 36
            },
            {
                nodeId: 'SN-C301',
                nodeName: 'Node Mới Lắp Đặt C1 (Đang bay đến)',
                zoneName: 'Khu Ươm Giống Mới',
                syncState: 'PENDING_COLLECTION',
                recordsCount: 0
            },
            {
                nodeId: 'SN-C302',
                nodeName: 'Cảm biến Nhà Kính Vi Sinh',
                zoneName: 'Khu Ươm Giống Mới',
                syncState: 'PENDING_COLLECTION',
                recordsCount: 0
            }
        ]
    },
    {
        id: 'MSN-2026-003',
        flightCode: 'FLIGHT-GAMMA-03',
        uavId: 'UAV-AGRI-01 (Cánh bằng VTOL)',
        status: 'SCHEDULED',
        targetZone: 'Toàn bộ 4 phân khu nông trại (Bay quét chiều)',
        totalNodes: 5,
        collectedNodes: 0,
        batteryStart: 100,
        batteryCurrent: 100,
        altitudeMeters: 50,
        speedMs: 10.0,
        nodeDetails: [
            { nodeId: 'SN-A101', nodeName: 'Cảm biến Đất & Vi Khí Hậu 01', zoneName: 'Khu Trồng Dâu Tây A', syncState: 'PENDING_COLLECTION', recordsCount: 0 },
            { nodeId: 'SN-A102', nodeName: 'Cảm biến Đất 02', zoneName: 'Khu Trồng Dâu Tây A', syncState: 'PENDING_COLLECTION', recordsCount: 0 },
            { nodeId: 'SN-B201', nodeName: 'Cảm biến Cà Phê B', zoneName: 'Khu Cà Phê Sườn Đồi', syncState: 'PENDING_COLLECTION', recordsCount: 0 },
            { nodeId: 'SN-C301', nodeName: 'Node Mới Lắp Đặt C1', zoneName: 'Khu Ươm Giống Mới', syncState: 'PENDING_COLLECTION', recordsCount: 0 },
            { nodeId: 'SN-W401', nodeName: 'Cảm biến Hồ Thủy Canh & pH', zoneName: 'Khu Thủy Canh Dinh Dưỡng', syncState: 'PENDING_COLLECTION', recordsCount: 0 }
        ]
    }
];