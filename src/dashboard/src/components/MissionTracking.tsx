import { useState } from 'react';
import { MOCK_MISSIONS } from '../mocks/mockMissions';
import type { UavMission, DataSyncState } from '../types/mission';
import {
    Plane,
    CheckCircle2,
    Clock,
    Radio,
    Database,
    HardDrive,
    Battery,
    Gauge,
    MapPin,
    ArrowUpRight,
    Wifi,
    Sparkles
} from 'lucide-react';

export function MissionTracking() {
    const [missions] = useState<UavMission[]>(MOCK_MISSIONS);
    const [selectedMission, setSelectedMission] = useState<UavMission>(MOCK_MISSIONS[1]); // Mặc định chọn nhiệm vụ đang bay

    // Thống kê nhanh F15 & F17
    const totalMissions = missions.length;
    const inProgressMissions = missions.filter(m => m.status === 'IN_PROGRESS').length;
    const totalNodesPlanned = missions.reduce((acc, m) => acc + m.totalNodes, 0);
    const totalNodesSynced = missions
        .flatMap(m => m.nodeDetails)
        .filter(n => n.syncState === 'SYNCED_BACKEND').length;

    // Hàm render nhãn 3 trạng thái đồng bộ ngoại tuyến (F17)
    const renderSyncBadge = (state: DataSyncState) => {
        switch (state) {
            case 'SYNCED_BACKEND':
                return (
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                        <Database className="w-3.5 h-3.5 text-emerald-600" />
                        Đã đồng bộ lên Máy chủ
                    </span>
                );
            case 'COLLECTED_OFFLINE':
                return (
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200 animate-pulse">
                        <HardDrive className="w-3.5 h-3.5 text-amber-600" />
                        Đã nhận qua LoRa (Lưu tạm trên UAV)
                    </span>
                );
            case 'PENDING_COLLECTION':
                return (
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-500 border border-slate-200">
                        <Clock className="w-3.5 h-3.5 text-slate-400" />
                        Chờ UAV bay qua thu thập
                    </span>
                );
        }
    };

    return (
        <div className="space-y-4">
            {/* 4 THẺ CHỈ SỐ NHIỆM VỤ UAV (F15 / F17) */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3.5">
                <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-xs flex items-center justify-between">
                    <div>
                        <p className="text-xs text-slate-500">Tổng chuyến bay trong ngày</p>
                        <p className="text-xl font-bold text-slate-900 mt-0.5 font-mono">{totalMissions} <span className="text-xs font-normal text-slate-400">chuyến</span></p>
                    </div>
                    <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-700 flex items-center justify-center">
                        <Plane className="w-4 h-4 -rotate-45" />
                    </div>
                </div>

                <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-xs flex items-center justify-between">
                    <div>
                        <p className="text-xs text-slate-500">Chuyến bay đang thực hiện</p>
                        <p className="text-xl font-bold text-emerald-600 mt-0.5 font-mono">{inProgressMissions} <span className="text-xs font-normal text-slate-400">chuyến</span></p>
                    </div>
                    <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                        <Radio className="w-4 h-4 animate-spin" />
                    </div>
                </div>

                <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-xs flex items-center justify-between">
                    <div>
                        <p className="text-xs text-slate-500">Đã nạp lên Backend (F17)</p>
                        <p className="text-xl font-bold text-teal-600 mt-0.5 font-mono">{totalNodesSynced} <span className="text-xs font-normal text-slate-400">node</span></p>
                    </div>
                    <div className="w-8 h-8 rounded-lg bg-teal-50 text-teal-600 flex items-center justify-center">
                        <Database className="w-4 h-4" />
                    </div>
                </div>

                <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-xs flex items-center justify-between">
                    <div>
                        <p className="text-xs text-slate-500">Kế hoạch thu thập thực địa</p>
                        <p className="text-xl font-bold text-slate-700 mt-0.5 font-mono">{totalNodesPlanned} <span className="text-xs font-normal text-slate-400">node</span></p>
                    </div>
                    <div className="w-8 h-8 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center">
                        <MapPin className="w-4 h-4" />
                    </div>
                </div>
            </div>

            {/* BỐ CỤC 2 CỘT: DANH SÁCH CHUYẾN BAY (BÊN TRÁI) & CHI TIẾT ĐỒNG BỘ NODE (BÊN PHẢI) */}
            <div className="grid grid-cols-12 gap-4 items-start">

                {/* CỘT TRÁI (5 CỘT): DANH SÁCH CÁC CHUYẾN BAY UAV */}
                <div className="col-span-12 lg:col-span-5 bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
                    <div className="p-3.5 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between">
                        <h3 className="font-bold text-xs text-slate-800 uppercase tracking-wider flex items-center gap-1.5">
                            <Plane className="w-3.5 h-3.5 text-emerald-600" />
                            Lịch trình & Tiến độ chuyến bay ({missions.length})
                        </h3>
                        <span className="text-[11px] text-slate-400">Chọn chuyến để xem</span>
                    </div>

                    <div className="divide-y divide-slate-100">
                        {missions.map(mission => {
                            const isSelected = selectedMission.id === mission.id;
                            const percent = Math.round((mission.collectedNodes / mission.totalNodes) * 100);

                            return (
                                <div
                                    key={mission.id}
                                    onClick={() => setSelectedMission(mission)}
                                    className={`p-3.5 cursor-pointer transition ${isSelected
                                            ? 'bg-emerald-50/70 border-l-4 border-emerald-600'
                                            : 'hover:bg-slate-50 border-l-4 border-transparent'
                                        }`}
                                >
                                    <div className="flex items-start justify-between gap-2">
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <span className="font-mono font-bold text-xs text-slate-900">{mission.flightCode}</span>
                                                <span className="text-[10px] font-mono text-slate-400">({mission.id})</span>
                                            </div>
                                            <p className="text-xs text-slate-600 mt-0.5 font-medium">{mission.uavId}</p>
                                        </div>

                                        <div>
                                            {mission.status === 'IN_PROGRESS' && (
                                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200">
                                                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-ping"></span>
                                                    Đang bay
                                                </span>
                                            )}
                                            {mission.status === 'COMPLETED' && (
                                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-slate-100 text-slate-700">
                                                    <CheckCircle2 className="w-3 h-3 text-emerald-600" /> Hoàn thành
                                                </span>
                                            )}
                                            {mission.status === 'SCHEDULED' && (
                                                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-blue-50 text-blue-700 border border-blue-200">
                                                    <Clock className="w-3 h-3" /> Đã lên lịch
                                                </span>
                                            )}
                                        </div>
                                    </div>

                                    <div className="mt-2 text-xs text-slate-500 flex items-center gap-1 truncate">
                                        <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
                                        <span className="truncate">{mission.targetZone}</span>
                                    </div>

                                    {/* Thanh tiến độ thu thập node */}
                                    <div className="mt-3 pt-2 border-t border-slate-100/80">
                                        <div className="flex items-center justify-between text-[11px] mb-1">
                                            <span className="text-slate-500">Tiến độ thu thập thực địa:</span>
                                            <span className="font-mono font-bold text-slate-800">
                                                {mission.collectedNodes}/{mission.totalNodes} node ({percent}%)
                                            </span>
                                        </div>
                                        <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
                                            <div
                                                className={`h-full rounded-full transition-all duration-500 ${mission.status === 'COMPLETED' ? 'bg-emerald-600' : 'bg-emerald-500'
                                                    }`}
                                                style={{ width: `${percent}%` }}
                                            ></div>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>

                {/* CỘT PHẢI (7 CỘT): CHI TIẾT 3 TRẠNG THÁI ĐỒNG BỘ NGOẠI TUYẾN (F17) */}
                <div className="col-span-12 lg:col-span-7 space-y-4">

                    {/* Thông tin chuyến bay đang chọn */}
                    <div className="bg-white rounded-xl border border-slate-200 shadow-xs p-4 space-y-3.5">
                        <div className="flex flex-wrap items-center justify-between border-b border-slate-100 pb-3 gap-2">
                            <div>
                                <div className="flex items-center gap-2">
                                    <h3 className="font-bold text-sm text-slate-900">{selectedMission.flightCode}</h3>
                                    <span className="text-xs font-mono text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                                        {selectedMission.id}
                                    </span>
                                </div>
                                <p className="text-xs text-slate-500 mt-0.5">{selectedMission.targetZone}</p>
                            </div>

                            {/* Thông số bay thực tế */}
                            <div className="flex items-center gap-3 text-xs font-mono text-slate-600 bg-slate-50 px-3 py-1.5 rounded-lg border border-slate-200">
                                <span className="flex items-center gap-1">
                                    <Battery className="w-3.5 h-3.5 text-emerald-600" /> Pin: {selectedMission.batteryCurrent}%
                                </span>
                                <span className="text-slate-300">|</span>
                                <span className="flex items-center gap-1">
                                    <ArrowUpRight className="w-3.5 h-3.5 text-blue-500" /> Cao: {selectedMission.altitudeMeters}m
                                </span>
                                <span className="text-slate-300">|</span>
                                <span className="flex items-center gap-1">
                                    <Gauge className="w-3.5 h-3.5 text-amber-500" /> V: {selectedMission.speedMs} m/s
                                </span>
                            </div>
                        </div>

                        {/* Chú thích 3 Trạng thái Đồng bộ F17 */}
                        <div className="bg-slate-50 rounded-lg p-3 border border-slate-200/80 space-y-2">
                            <div className="text-[11px] font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                                <Sparkles className="w-3.5 h-3.5 text-emerald-600" />
                                Cơ chế đồng bộ dữ liệu ngoại tuyến (Offline Sync - F17)
                            </div>
                            <p className="text-xs text-slate-500 leading-relaxed">
                                Do các node cảm biến nằm rải rác ngoài cánh đồng không có Wi-Fi/4G, dữ liệu trải qua 3 giai đoạn:
                                <b> Chờ UAV thu thập</b> → <b>Lưu tạm thẻ nhớ trên UAV qua sóng LoRa</b> → <b>UAV về trạm nạp lên Server Spring Boot</b>.
                            </p>
                        </div>

                        {/* BẢNG CHI TIẾT TỪNG TRẠM CẢM BIẾN TRONG CHUYẾN BAY */}
                        <div className="border border-slate-200 rounded-lg overflow-hidden">
                            <div className="bg-slate-100/80 px-3 py-2 text-[11px] font-bold text-slate-700 grid grid-cols-12 gap-2 border-b border-slate-200">
                                <div className="col-span-4">Node cảm biến / Vị trí</div>
                                <div className="col-span-5">Trạng thái đồng bộ (F17)</div>
                                <div className="col-span-3 text-right">Tín hiệu LoRa & Dữ liệu</div>
                            </div>

                            <div className="divide-y divide-slate-100 text-xs">
                                {selectedMission.nodeDetails.map(node => (
                                    <div key={node.nodeId} className="p-3 grid grid-cols-12 gap-2 items-center hover:bg-slate-50/60 transition">

                                        {/* Cột 1: Thông tin Node */}
                                        <div className="col-span-4">
                                            <div className="font-mono font-bold text-slate-900">{node.nodeId}</div>
                                            <div className="text-[11px] text-slate-500 truncate">{node.nodeName}</div>
                                            <div className="text-[10px] text-slate-400 mt-0.5">{node.zoneName}</div>
                                        </div>

                                        {/* Cột 2: Trạng thái 3 bước F17 */}
                                        <div className="col-span-5 space-y-1">
                                            {renderSyncBadge(node.syncState)}
                                            {node.collectedAt && (
                                                <div className="text-[10px] text-slate-400 flex items-center gap-1">
                                                    <Radio className="w-2.5 h-2.5 text-slate-400" /> UAV nhận: {new Date(node.collectedAt).toLocaleTimeString()}
                                                </div>
                                            )}
                                            {node.syncedToBackendAt && (
                                                <div className="text-[10px] text-emerald-600 flex items-center gap-1 font-medium">
                                                    <Database className="w-2.5 h-2.5" /> Lên server: {new Date(node.syncedToBackendAt).toLocaleTimeString()}
                                                </div>
                                            )}
                                        </div>

                                        {/* Cột 3: Chỉ số LoRa & Gói tin */}
                                        <div className="col-span-3 text-right space-y-0.5 font-mono">
                                            {node.loraRssi ? (
                                                <div className="flex items-center justify-end gap-1 text-[11px] text-slate-700">
                                                    <Wifi className="w-3 h-3 text-emerald-600" />
                                                    <span>{node.loraRssi} dBm</span>
                                                </div>
                                            ) : (
                                                <span className="text-slate-400 text-[11px]">--</span>
                                            )}
                                            <div className="text-[10px] text-slate-500 font-sans">
                                                {node.recordsCount > 0 ? (
                                                    <span className="font-medium text-slate-800">{node.recordsCount} bản ghi</span>
                                                ) : (
                                                    <span className="italic text-slate-400">Chưa nạp</span>
                                                )}
                                            </div>
                                        </div>

                                    </div>
                                ))}
                            </div>
                        </div>

                    </div>

                </div>

            </div>
        </div>
    );
}