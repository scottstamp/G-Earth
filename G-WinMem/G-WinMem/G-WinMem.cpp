// G-WinMem.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"
#include <Windows.h>
#include <iostream>
#include <stdlib.h>
#include <string>
#include <iterator>
#include <iphlpapi.h>
#include <psapi.h>
#include <Tlhelp32.h>
#include <WbemIdl.h>
#include <winternl.h>
#include <comdef.h>
#include <vector>
#include <filesystem>
#include <thread>

#include "Process.h"

#pragma comment(lib, "iphlpapi.lib")
#pragma comment(lib, "Ws2_32.lib")
#pragma comment(lib, "Ntdll.lib")


PVOID GetPebAddress(HANDLE pHandle)
{
	PROCESS_BASIC_INFORMATION pbi;
	NtQueryInformationProcess(pHandle, ProcessBasicInformation, &pbi, sizeof(pbi), nullptr);

	return pbi.PebBaseAddress;
}

bool IsFlashProcess(int pid)
{
	PPROCESS_BASIC_INFORMATION pbi = nullptr;
	PEB peb = { NULL };
	RTL_USER_PROCESS_PARAMETERS processParams = { NULL };

	auto hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
	if (hProcess == INVALID_HANDLE_VALUE) {
		std::cerr << "Invalid process handle\n";
		return false;
	}

	auto heap = GetProcessHeap();
	auto pbiSize = sizeof(PROCESS_BASIC_INFORMATION);

	pbi = static_cast<PPROCESS_BASIC_INFORMATION>(HeapAlloc(heap, HEAP_ZERO_MEMORY, pbiSize));

	if (!pbi)
	{
		CloseHandle(hProcess);
		return false;
	}

	auto pebAddr = GetPebAddress(hProcess);

	SIZE_T bytesRead;
	if (ReadProcessMemory(hProcess, pebAddr, &peb, sizeof(peb), &bytesRead))
	{
		bytesRead = 0;
		if (ReadProcessMemory(hProcess, peb.ProcessParameters, &processParams, sizeof(RTL_USER_PROCESS_PARAMETERS), &bytesRead))
		{
			if (processParams.CommandLine.Length > 0)
			{
				auto buffer = static_cast<WCHAR *>(malloc(processParams.CommandLine.Length * sizeof(WCHAR)));

				if (buffer)
				{
					if (ReadProcessMemory(hProcess, processParams.CommandLine.Buffer, buffer, processParams.CommandLine.Length, &bytesRead))
					{
						const _bstr_t b(buffer);
						if (strstr(static_cast<char const *>(b), "ppapi") || strstr(static_cast<char const *>(b), "plugin-container"))
						{
							CloseHandle(hProcess);
							HeapFree(heap, 0, pbi);
							return true;
						}			
					}
				}
				free(buffer);
			}
		}
	}

	CloseHandle(hProcess);
	HeapFree(heap, 0, pbi);

	return false;
}

std::vector<int> GetProcessId(std::string host, int port)
{
	std::vector<int> processIds;
	DWORD size;

	GetExtendedTcpTable(nullptr, &size, FALSE, AF_INET, TCP_TABLE_OWNER_PID_ALL, 0);

	auto tcp_pid = new MIB_TCPTABLE_OWNER_PID[size];

	if(GetExtendedTcpTable(tcp_pid, &size, FALSE, AF_INET, TCP_TABLE_OWNER_PID_ALL, NULL) != NO_ERROR)
	{
		std::cerr << "Failed to get TCP Table\n";
		return processIds;
	}

	for (DWORD i = 0; i < tcp_pid->dwNumEntries; i++)
	{
		auto *owner_pid = &tcp_pid->table[i];
		DWORD ip = owner_pid->dwRemoteAddr;
		std::string ipString = std::to_string(ip & 0xFF) + "." + std::to_string(ip >> 8 & 0xFF) + 
			"." + std::to_string(ip >> 16 & 0xFF) + "." + std::to_string(ip >> 24 & 0xFF);
		if (ntohs(owner_pid->dwRemotePort) == port &&
			ipString == host) {
			auto hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, owner_pid->dwOwningPid);

			if (hProcess)
			{
				TCHAR procName[MAX_PATH];
				if (GetModuleFileNameEx(hProcess, nullptr, procName, MAX_PATH))
				{
					auto hProcessSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
					if (hProcessSnap == INVALID_HANDLE_VALUE)
					{
						std::cerr << "Failed\n";
						return processIds;
					}

					PROCESSENTRY32 pe32;
					pe32.dwSize = sizeof(PROCESSENTRY32);

					if (!Process32First(hProcessSnap, &pe32))
					{
						std::cerr << "Process32First failed\n";
						CloseHandle(hProcessSnap);
						return processIds;
					}
					std::experimental::filesystem::path p(procName);

					do
					{
						if (pe32.szExeFile == p.filename()) {
							processIds.push_back(pe32.th32ProcessID);
						}

					} while (Process32Next(hProcessSnap, &pe32));

					CloseHandle(hProcessSnap);
					break;
				}
				CloseHandle(hProcess);
			}
		}
	}
	return processIds;
}

int main(int argc, char **argv)
{

	std::vector<u_char *> cachedOffsets;
	auto usingCache = false;
	
	if (argc > 3)
		if (!strncmp(argv[3], "-c", 2)) // Cache mode
		{
			usingCache = true;

			for (auto i = 0; i < argc - 4; i++)
				cachedOffsets.push_back(reinterpret_cast<u_char *>(strtoull(argv[4 + i], nullptr, 16)));
		}
	
	if (argc >= 3) {
	    auto pids = GetProcessId(argv[1], strtol(argv[2], nullptr, 10));
		std::vector<std::thread> threads;

		for (auto pid : pids) {
			if (IsFlashProcess(pid)) {
				auto p = new Process(pid);
				if (usingCache)
					p->PrintCachedResults(cachedOffsets);
				else
					threads.push_back(std::thread (std::bind(&Process::PrintRC4Possibilities, p)));
			}
		}

		for (auto i = 0; i < threads.size(); i++)
			if (threads[i].joinable())
				threads[i].join();

		if (pids.empty())
			std::cerr << "No pids found\n";
	}

    return 0;
}