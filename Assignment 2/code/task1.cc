#include "ns3/ipv4-flow-classifier.h" 
#include "ns3/aodv-helper.h"
#include "ns3/applications-module.h"
#include "ns3/core-module.h"
#include "ns3/internet-module.h"
#include "ns3/log.h"
#include "ns3/mobility-module.h"
#include "ns3/network-module.h"
#include "ns3/wifi-module.h"
#include "ns3/flow-monitor-helper.h"


using namespace ns3;

NS_LOG_COMPONENT_DEFINE("Task1Simulation");

NodeContainer CreateNodes(uint32_t nNodes) {
    NodeContainer nodes;
    nodes.Create(nNodes);
    return nodes;
}


NetDeviceContainer InstallWifi(NodeContainer &nodes) {
    WifiHelper wifi;
    YansWifiChannelHelper channel = YansWifiChannelHelper::Default();
    YansWifiPhyHelper phy = YansWifiPhyHelper();
    phy.SetChannel(channel.Create());

    WifiMacHelper mac;
    Ssid ssid = Ssid("Task1-WiFi");
    mac.SetType("ns3::AdhocWifiMac", "Ssid", SsidValue(ssid));

    return wifi.Install(phy, mac, nodes);
}

void ConfigureMobility(NodeContainer &nodes, double nodeSpeed) {
    
    MobilityHelper mobility;
     mobility.SetPositionAllocator("ns3::RandomRectanglePositionAllocator",
                                   "X", StringValue("ns3::UniformRandomVariable[Min=0.0|Max=100.0]"),
                                   "Y", StringValue("ns3::UniformRandomVariable[Min=0.0|Max=100.0]"));
     mobility.SetMobilityModel("ns3::RandomWaypointMobilityModel",
                               "Speed", StringValue("ns3::ConstantRandomVariable[Constant=" + std::to_string(nodeSpeed) + "]"),
                               "Pause", StringValue("ns3::ConstantRandomVariable[Constant=0.0]"),
                               "PositionAllocator", StringValue("ns3::RandomRectanglePositionAllocator"));
     mobility.Install(nodes);
}


Ipv4InterfaceContainer AssignIP(NetDeviceContainer &devices) {
    Ipv4AddressHelper ipv4;
    ipv4.SetBase("10.1.1.0", "255.255.255.0");
    return ipv4.Assign(devices);
}


void CollectMetrics(Ptr<FlowMonitor> monitor, FlowMonitorHelper &flowHelper, 
                    std::string csvFileName, double simulationTime, 
                    uint32_t nNodes, uint32_t packetRate, double nodeSpeed) {
    monitor->CheckForLostPackets();
    Ptr<Ipv4FlowClassifier> classifier = DynamicCast<Ipv4FlowClassifier>(flowHelper.GetClassifier());
    FlowMonitor::FlowStatsContainer stats = monitor->GetFlowStats();

    double throughput = 0.0, totalDelay = 0.0;
    uint32_t sentPackets = 0, receivedPackets = 0, droppedPackets = 0, totalReceivedPackets = 0;

    for (auto &flow : stats) {
        sentPackets += flow.second.txPackets;
        receivedPackets += flow.second.rxPackets;
        droppedPackets += (flow.second.txPackets - flow.second.rxPackets);

        throughput += (flow.second.rxBytes * 8.0 / (simulationTime * 1e3)); // Kbps
        if (flow.second.rxPackets > 0) {
            totalDelay += flow.second.delaySum.GetSeconds();
            totalReceivedPackets += flow.second.rxPackets;
        }
    }

    double avgDelay = (totalReceivedPackets > 0) ? (totalDelay / totalReceivedPackets) * 1e3 : 0.0;
    double deliveryRatio = (sentPackets > 0) ? (double)receivedPackets / sentPackets : 0.0;
    double dropRatio = (sentPackets > 0) ? (double)droppedPackets / sentPackets : 0.0;

    std::ofstream csvFile(csvFileName, std::ios::out | std::ios::app);

    csvFile.seekp(0, std::ios::end);
    if (csvFile.tellp() == 0) {
        csvFile << "Nodes,PacketRate,NodeSpeed,SentPackets,Throughput,DeliveryRatio,DropRatio,AvgDelay\n";
    }

    csvFile << nNodes << "," 
            << packetRate << "," 
            << nodeSpeed << "," 
            << sentPackets << "," 
            << throughput << "," 
            << deliveryRatio << "," 
            << dropRatio << "," 
            << avgDelay << "\n";
    csvFile.close();

    NS_LOG_UNCOND("Simulation Results:");
    NS_LOG_UNCOND("Nodes: " << nNodes);
    NS_LOG_UNCOND("Packet Rate: " << packetRate);
    NS_LOG_UNCOND("Node Speed: " << nodeSpeed);
    NS_LOG_UNCOND("Throughput: " << throughput << " Kbps");
    NS_LOG_UNCOND("Packet Delivery Ratio: " << deliveryRatio);
    NS_LOG_UNCOND("Packet Drop Ratio: " << dropRatio);
    NS_LOG_UNCOND("Average End-to-End Delay: " << avgDelay << " ms");
}




int main(int argc, char *argv[]) {
    uint32_t nNodes = 20;
    uint32_t packetRate = 100;
    double nodeSpeed = 5.0;
    double simulationTime = 20.0;
    std::string csvFileName = "task1_result.csv";

    CommandLine cmd;
    cmd.AddValue("nNodes", "Number of nodes", nNodes);
    cmd.AddValue("packetRate", "Packets per second", packetRate);
    cmd.AddValue("nodeSpeed", "Speed of nodes in m/s", nodeSpeed);
    cmd.AddValue("csvFileName", "CSV file name to store results", csvFileName);
    cmd.Parse(argc, argv);

    NS_LOG_UNCOND("Running Task 1 Simulation with " << nNodes << " nodes.");

    NodeContainer nodes = CreateNodes(nNodes);
    
    NetDeviceContainer devices = InstallWifi(nodes);
   
    ConfigureMobility(nodes, nodeSpeed);
    

    AodvHelper aodv;
    InternetStackHelper internet;
    internet.SetRoutingHelper(aodv);
    internet.Install(nodes);

  

    Ipv4InterfaceContainer interfaces = AssignIP(devices);

    // InstallApplications
    uint16_t port = 9;
    OnOffHelper onoff("ns3::UdpSocketFactory", Address());
    onoff.SetAttribute("OnTime", StringValue("ns3::ConstantRandomVariable[Constant=1.0]"));
    onoff.SetAttribute("OffTime", StringValue("ns3::ConstantRandomVariable[Constant=0.0]"));
    onoff.SetAttribute("PacketSize", UintegerValue(64));
    onoff.SetAttribute("DataRate", StringValue(std::to_string(packetRate * 64 * 8 ) + "bps"));

    for (uint32_t i = 0; i < nNodes; i++)
    {
        AddressValue remoteAddress(InetSocketAddress(interfaces.GetAddress((i + 5)%nNodes), port));
        onoff.SetAttribute("Remote", remoteAddress);
        ApplicationContainer app = onoff.Install(nodes.Get(i));
        app.Start(Seconds(1.0));
        app.Stop(Seconds(simulationTime));
    }

    FlowMonitorHelper flowHelper;
    Ptr<FlowMonitor> monitor = flowHelper.InstallAll();

    Simulator::Stop(Seconds(simulationTime));
    Simulator::Run();

    CollectMetrics(monitor, flowHelper, csvFileName, simulationTime, nNodes, packetRate, nodeSpeed);

    Simulator::Destroy();
    return 0;
}